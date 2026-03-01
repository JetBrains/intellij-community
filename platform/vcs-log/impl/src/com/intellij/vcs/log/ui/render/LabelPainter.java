// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.render;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.AntialiasingType;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ui.BranchPresentation;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Range;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;
import com.intellij.util.ui.JBValue.JBValueGroup;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.UpdateScaleHelper;
import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.DetachedHeadRefGroup;
import com.intellij.vcs.log.ui.VcsBookmarkRef;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LabelPainter {
  private static final JBValueGroup JBVG = new JBValueGroup();
  public static final JBValue TOP_TEXT_PADDING = JBVG.value(1);
  public static final JBValue BOTTOM_TEXT_PADDING = JBVG.value(2);
  public static final JBValue RIGHT_PADDING = JBVG.value(4);
  public static final JBValue LEFT_PADDING = JBVG.value(4);
  public static final JBValue COMPACT_MIDDLE_PADDING = JBVG.value(6);
  public static final JBValue MIDDLE_PADDING = JBVG.value(12);
  public static final JBValue ICON_TEXT_PADDING = JBVG.value(1);
  public static final JBValue ICON_ADDITIONAL_PADDING = JBVG.value(3);
  public static final JBValue LABEL_ARC = JBVG.value(6);
  private static final int MAX_LENGTH = 22;
  private static final String TWO_DOTS = "..";
  private static final String SEPARATOR = "/";

  private final @NotNull JComponent myComponent;
  private final @NotNull LabelIconCache myIconCache;
  private final @NotNull UpdateScaleHelper myUpdateScaleHelper = new UpdateScaleHelper();

  private @NotNull List<Presentation> myLabels = new ArrayList<>();
  private int myHeight = JBUIScale.scale(22);
  private int myWidth = 0;
  protected @NotNull Color myBackground = UIUtil.getTableBackground();
  private @Nullable Color myGreyBackground = null;
  private @NotNull Color myForeground = UIUtil.getTableForeground();
  private boolean myIsOpaque = true;

  private boolean myCompact;
  private boolean myLeftAligned;
  private @Nullable Range<Integer> myWarningRange;

  @ApiStatus.Internal
  public LabelPainter(@NotNull JComponent component,
                      @NotNull LabelIconCache iconCache) {
    myComponent = component;
    myIconCache = iconCache;
  }

  public void updateHeight() {
    FontMetrics metrics = myComponent.getFontMetrics(getReferenceFont());
    myHeight = metrics.getHeight() + TOP_TEXT_PADDING.get() + BOTTOM_TEXT_PADDING.get();
  }

  public void customizePainter(@NotNull Color background,
                               @NotNull Color foreground,
                               boolean isSelected,
                               int availableWidth,
                               @NotNull List<? extends RefGroup> refGroups) {
    customizePainter(background, foreground, isSelected, availableWidth, refGroups, Collections.emptyList());
  }

  public void customizePainter(@NotNull Color background,
                               @NotNull Color foreground,
                               boolean isSelected,
                               int availableWidth,
                               @NotNull List<? extends RefGroup> refGroups,
                               @NotNull List<VcsBookmarkRef> bookmarks) {
    myBackground = background;
    myForeground = foreground;

    updateHeight();
    FontMetrics metrics = myComponent.getFontMetrics(getReferenceFont());
    myGreyBackground = ExperimentalUI.isNewUI() ? null : calculateGreyBackground(refGroups, background, isSelected, myCompact);
    myWarningRange = null;
    Presentations presentation =
      calculatePresentation(refGroups, bookmarks, metrics, myGreyBackground != null ? myGreyBackground : myBackground,
                            availableWidth, myCompact);

    myLabels = presentation.list;
    myWidth = presentation.width;
  }

  private @NotNull Presentations calculatePresentation(@NotNull List<? extends RefGroup> refGroups,
                                                       @NotNull List<VcsBookmarkRef> bookmarks,
                                                       @NotNull FontMetrics fontMetrics,
                                                       @NotNull Color background,
                                                       int availableWidth,
                                                       boolean compact) {
    if (refGroups.isEmpty() && bookmarks.isEmpty()) {
      return new Presentations(Collections.emptyList(), 0);
    }

    int height = fontMetrics.getHeight();
    int sidePaddingsWidth = LEFT_PADDING.get() + RIGHT_PADDING.get();
    int middlePadding = compact ? COMPACT_MIDDLE_PADDING.get() : MIDDLE_PADDING.get();

    List<Presentation> bookmarkLabels = processBookmarkLabels(bookmarks, background, height);
    if (refGroups.isEmpty()) {
      int width = getPresentationsWidth(bookmarkLabels, middlePadding);
      return new Presentations(bookmarkLabels, width > 0 ? width + sidePaddingsWidth : 0);
    }
    Presentation detachedHeadLabel = processDetachedHead(refGroups, fontMetrics);

    var detachedHeadWidth = detachedHeadLabel != null ? detachedHeadLabel.width + middlePadding : 0;
    int remainingWidth = availableWidth - getCurrentWidth(bookmarkLabels, middlePadding) - detachedHeadWidth - sidePaddingsWidth;
    List<Presentation> referenceLabels = processReferenceLabels(refGroups, fontMetrics, remainingWidth, background, height, compact);

    List<Presentation> result = getPresentations(bookmarkLabels, referenceLabels, detachedHeadLabel, middlePadding);
    int width = getPresentationsWidth(result, middlePadding);
    return new Presentations(result, width > 0 ? width + sidePaddingsWidth : 0);
  }

  private @NotNull List<Presentation> getPresentations(@NotNull List<Presentation> bookmarkLabels,
                                                       @NotNull List<Presentation> referenceLabels,
                                                       @Nullable Presentation detachedHeadLabel,
                                                       int middlePadding) {
    if (bookmarkLabels.isEmpty() && detachedHeadLabel == null) return referenceLabels;

    int detachedHeadCount = detachedHeadLabel != null ? 1 : 0;

    List<Presentation> result = new ArrayList<>(bookmarkLabels.size() + referenceLabels.size() + detachedHeadCount);
    if (isLeftAligned()) {
      result.addAll(bookmarkLabels);
    }
    if (detachedHeadLabel != null) {
      myWarningRange = getSectionRange(result, middlePadding, detachedHeadLabel);
      result.add(detachedHeadLabel);
    }
    result.addAll(referenceLabels);
    if (!isLeftAligned()) {
      result.addAll(bookmarkLabels);
    }
    return result;
  }

  private static @NotNull Range<Integer> getSectionRange(List<Presentation> addedLabels, int middlePadding, Presentation section) {
    int currentWidth = getCurrentWidth(addedLabels, middlePadding) + LEFT_PADDING.get();
    return new Range<>(currentWidth, currentWidth + section.width);
  }

  private static int getCurrentWidth(@NotNull List<Presentation> result, int middlePadding) {
    int presentationsWidth = getPresentationsWidth(result, middlePadding);
    return presentationsWidth + (presentationsWidth > 0 ? middlePadding : 0);
  }

  private static int getPresentationsWidth(@NotNull List<Presentation> presentations, int middlePadding) {
    if (presentations.isEmpty()) return 0;
    return presentations.stream().mapToInt(Presentation::width).sum() + middlePadding * (presentations.size() - 1);
  }

  private static @Nullable Presentation processDetachedHead(@NotNull List<? extends RefGroup> refGroups,
                                                            @NotNull FontMetrics fontMetrics) {
    if (refGroups.isEmpty()) return null;
    RefGroup firstRef = refGroups.getFirst();
    if (firstRef instanceof DetachedHeadRefGroup) {
      return getIconTextPresentation(fontMetrics, firstRef.getName(), AllIcons.General.Warning,
                                     JBUI.CurrentTheme.Label.warningForeground(), ICON_ADDITIONAL_PADDING.get());
    }
    return null;
  }

  private @NotNull List<Presentation> processBookmarkLabels(@NotNull List<VcsBookmarkRef> bookmarks,
                                                            @NotNull Color background,
                                                            int height) {
    return bookmarks.stream()
      .map(it -> new BookmarkIcon(myComponent, height, background, it))
      .map(icon -> new Presentation("", icon, icon.getIconWidth(), 0, null))
      .toList();
  }

  private @NotNull List<Presentation> processReferenceLabels(@NotNull List<? extends RefGroup> refGroups,
                                                             @NotNull FontMetrics fontMetrics,
                                                             int availableWidth,
                                                             @NotNull Color background,
                                                             int height,
                                                             boolean compact) {
    List<Presentation> labels = new ArrayList<>();
    int referencesWidth = 0;
    int middlePadding = compact ? COMPACT_MIDDLE_PADDING.get() : MIDDLE_PADDING.get();
    for (int i = 0; i < refGroups.size(); i++) {
      RefGroup group = refGroups.get(i);

      if (group instanceof DetachedHeadRefGroup) {
        continue;
      }

      int doNotFitWidth = 0;
      if (!compact && i < refGroups.size() - 1) {
        LabelIcon lastIcon = getIcon(height, background, getColors(refGroups.subList(i + 1, refGroups.size())));
        doNotFitWidth = lastIcon.getIconWidth();
      }

      List<Color> colors = group.getColors();
      LabelIcon labelIcon = getIcon(height, background, colors);
      boolean isLast = i == refGroups.size() - 1;
      int newWidth = referencesWidth + labelIcon.getIconWidth() + (isLast ? 0 : middlePadding);

      String text = getGroupText(group, fontMetrics, availableWidth - newWidth - doNotFitWidth);
      newWidth += getTextWidth(fontMetrics, text);

      // for compact case all references are already combined to one, no need to combine them again
      if (!compact && availableWidth - newWidth - doNotFitWidth < 0) {
        LabelIcon lastIcon = getIcon(height, background, getColors(refGroups.subList(i, refGroups.size())));
        String name = labels.isEmpty() ? text : "";
        labels.add(getIconTextPresentation(fontMetrics, name, lastIcon));
        break;
      }
      else {
        labels.add(getIconTextPresentation(fontMetrics, text, labelIcon));
        referencesWidth = newWidth;
      }
    }
    return labels;
  }

  private static @NotNull Presentation getIconTextPresentation(@NotNull FontMetrics fontMetrics, @NotNull String text, @NotNull Icon icon) {
    return getIconTextPresentation(fontMetrics, text, icon, null, 0);
  }

  private static @NotNull Presentation getIconTextPresentation(@NotNull FontMetrics fontMetrics,
                                                               @NotNull String text,
                                                               @NotNull Icon icon,
                                                               @Nullable Color color,
                                                               int iconPadding) {
    int iconTextWidth = icon.getIconWidth() + iconPadding + getTextWidth(fontMetrics, text);
    return new Presentation(text, icon, iconTextWidth, iconPadding, color);
  }

  private static int getTextWidth(@NotNull FontMetrics fontMetrics, @NotNull String name) {
    int textWidth = fontMetrics.stringWidth(name);
    return textWidth > 0 ? getIconTextPadding() + textWidth : 0;
  }

  private @NotNull LabelIcon getIcon(int height, @NotNull Color background, @NotNull List<? extends Color> colors) {
    return myIconCache.getIcon(myComponent, height, background, colors);
  }

  private static @NotNull List<Color> getColors(@NotNull Collection<? extends RefGroup> groups) {
    LinkedHashMap<Color, Integer> usedColors = new LinkedHashMap<>();

    for (RefGroup group : groups) {
      List<Color> colors = group.getColors();
      for (Color color : colors) {
        Integer count = usedColors.get(color);
        if (count == null) count = 0;
        usedColors.put(color, count + 1);
      }
    }

    List<Color> result = new ArrayList<>();
    for (Map.Entry<Color, Integer> entry : usedColors.entrySet()) {
      result.add(entry.getKey());
      if (entry.getValue() > 1) {
        result.add(entry.getKey());
      }
    }

    return result;
  }

  private static @NotNull String getGroupText(@NotNull RefGroup group, @NotNull FontMetrics fontMetrics, int availableWidth) {
    return shortenRefName(group.getName(), fontMetrics, availableWidth);
  }

  private static @Nullable Color calculateGreyBackground(@NotNull List<? extends RefGroup> refGroups,
                                                         @NotNull Color background,
                                                         boolean isSelected,
                                                         boolean isCompact) {
    if (isSelected) return null;
    if (!isCompact) return BranchPresentation.getBranchPresentationBackground(background);

    for (RefGroup group : refGroups) {
      if (!group.getName().isEmpty()) return BranchPresentation.getBranchPresentationBackground(background);
    }

    return null;
  }

  private static @NotNull String shortenRefName(@NotNull @NlsSafe String refName, @NotNull FontMetrics fontMetrics, int availableWidth) {
    if (fontMetrics.stringWidth(refName) > availableWidth && refName.length() > MAX_LENGTH) {
      int separatorIndex = refName.indexOf(SEPARATOR);
      if (separatorIndex > TWO_DOTS.length()) {
        refName = TWO_DOTS + refName.substring(separatorIndex);
      }

      if (availableWidth > 0) {
        return VcsLogUiUtil.shortenTextToFit(refName, fontMetrics, availableWidth, MAX_LENGTH, StringUtil.ELLIPSIS);
      }
      return StringUtil.shortenTextWithEllipsis(refName, MAX_LENGTH, 0, StringUtil.ELLIPSIS);
    }
    return refName;
  }

  public void paint(@NotNull Graphics2D g2, int x, int y, int height) {
    if (myLabels.isEmpty()) return;

    GraphicsConfig config = GraphicsUtil.setupAAPainting(g2)
      .setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, getFractionalMetricsValue())
      .setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, getTextAntiAliasingValue());
    g2.setFont(getReferenceFont());
    g2.setStroke(new BasicStroke(1.5f));

    FontMetrics fontMetrics = g2.getFontMetrics();
    int baseLine = SimpleColoredComponent.getTextBaseLine(fontMetrics, height);

    if (myIsOpaque) {
      g2.setColor(myBackground);
      g2.fillRect(x, y, myWidth, height);
    }

    if (myGreyBackground != null && myCompact) {
      g2.setColor(myGreyBackground);
      g2.fillRect(x, y + baseLine - fontMetrics.getAscent() - TOP_TEXT_PADDING.get(),
                  myWidth,
                  fontMetrics.getHeight() + TOP_TEXT_PADDING.get() + BOTTOM_TEXT_PADDING.get());
    }

    x += LEFT_PADDING.get();

    for (Presentation label : myLabels) {
      Icon icon = label.icon;
      String text = label.text;

      if (myGreyBackground != null && !myCompact) {
        g2.setColor(myGreyBackground);
        g2.fill(new RoundRectangle2D.Double(x - MIDDLE_PADDING.get() / 3, y + baseLine - fontMetrics.getAscent() - TOP_TEXT_PADDING.get(),
                                            icon.getIconWidth() + fontMetrics.stringWidth(text) + 2 * MIDDLE_PADDING.get() / 3,
                                            fontMetrics.getHeight() + TOP_TEXT_PADDING.get() + BOTTOM_TEXT_PADDING.get(), LABEL_ARC.get(),
                                            LABEL_ARC.get()));
      }

      icon.paintIcon(null, g2, x, y + (height - icon.getIconHeight()) / 2);
      x += icon.getIconWidth();
      x += getIconTextPadding();
      x += label.iconPadding;
      g2.setColor(label.textColor != null ? label.textColor : myForeground);
      g2.drawString(text, x, y + baseLine);
      x += fontMetrics.stringWidth(text) + (myCompact ? COMPACT_MIDDLE_PADDING.get() : MIDDLE_PADDING.get());
    }

    config.restore();
  }

  private static int getIconTextPadding() {
    return ExperimentalUI.isNewUI() ? ICON_TEXT_PADDING.get() : 0;
  }

  private Object getTextAntiAliasingValue() {
    return Objects.requireNonNullElse(myComponent.getClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING),
                                      AntialiasingType.getKeyForCurrentScope(false));
  }

  private Object getFractionalMetricsValue() {
    return Objects.requireNonNullElse(myComponent.getClientProperty(RenderingHints.KEY_FRACTIONALMETRICS),
                                      UISettings.Companion.getPreferredFractionalMetricsValue());
  }

  public Dimension getSize() {
    if (myLabels.isEmpty()) return new Dimension();
    myUpdateScaleHelper.saveScaleAndRunIfChanged(this::updateHeight);
    return new Dimension(myWidth, myHeight);
  }

  public boolean isLeftAligned() {
    return myLeftAligned;
  }

  public static Font getReferenceFont() {
    Font font = GraphCommitCellRenderer.getLabelFont();
    return font.deriveFont(font.getSize() - 1f);
  }

  public boolean isCompact() {
    return myCompact;
  }

  public void setCompact(boolean compact) {
    myCompact = compact;
  }

  public void setLeftAligned(boolean leftAligned) {
    myLeftAligned = leftAligned;
  }

  /**
   * If set to true, painter paints all the pixels, including background pixels, so that the components underneath are not visible.
   * If set to false, the background is not painted.
   *
   * @param isOpaque true if all the pixels, including the background, should be painted
   */
  public void setOpaque(boolean isOpaque) {
    myIsOpaque = isOpaque;
  }

  public void clear() {
    myLabels.clear();
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public JComponent createTooltip(VcsLogData logData, Collection<VcsRef> refs, Collection<VcsBookmarkRef> bookmarks, double x) {
    if (myWarningRange != null && x >= myWarningRange.getFrom() && x < myWarningRange.getTo()) {
      JBLabel label = new JBLabel(String.format("<html><body><div style='width: %s;'>%s</div></body></html>",
                                                JBUI.scale(250),
                                                VcsLogBundle.message("vcs.log.references.detached.head.tooltip")));
      label.setForeground(UIUtil.getToolTipForeground());
      label.setFont(UIUtil.getToolTipFont());
      return label;
    }
    return new TooltipReferencesPanel(logData, refs, bookmarks);
  }

  private record Presentations(@NotNull List<Presentation> list, int width) {
  }

  private record Presentation(@NotNull String text, @NotNull Icon icon, int width, int iconPadding, @Nullable Color textColor) {
  }
}

