// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.render;

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBValue;
import com.intellij.util.ui.JBValue.JBValueGroup;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.UpdateScaleHelper;
import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.ui.VcsBookmarkRef;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.*;

import static com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent.getBranchPresentationBackground;

public class LabelPainter {
  private static final JBValueGroup JBVG = new JBValueGroup();
  public static final JBValue TOP_TEXT_PADDING = JBVG.value(1);
  public static final JBValue BOTTOM_TEXT_PADDING = JBVG.value(2);
  public static final JBValue RIGHT_PADDING = JBVG.value(4);
  public static final JBValue LEFT_PADDING = JBVG.value(4);
  public static final JBValue COMPACT_MIDDLE_PADDING = JBVG.value(2);
  public static final JBValue MIDDLE_PADDING = JBVG.value(12);
  public static final JBValue ICON_TEXT_PADDING = JBVG.value(1);
  public static final JBValue LABEL_ARC = JBVG.value(6);
  private static final int MAX_LENGTH = 22;
  private static final String TWO_DOTS = "..";
  private static final String SEPARATOR = "/";

  private final @NotNull JComponent myComponent;
  private final @NotNull LabelIconCache myIconCache;
  private final @NotNull UpdateScaleHelper myUpdateScaleHelper = new UpdateScaleHelper();

  protected @NotNull List<Pair<String, Icon>> myLabels = new ArrayList<>();
  private int myHeight = JBUIScale.scale(22);
  private int myWidth = 0;
  protected @NotNull Color myBackground = UIUtil.getTableBackground();
  private @Nullable Color myGreyBackground = null;
  private @NotNull Color myForeground = UIUtil.getTableForeground();
  private boolean myIsOpaque = true;

  private boolean myCompact;
  private boolean myLeftAligned;

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
    Pair<List<Pair<String, Icon>>, Integer> presentation =
      calculatePresentation(refGroups, bookmarks, metrics, myGreyBackground != null ? myGreyBackground : myBackground,
                            availableWidth, myCompact);

    myLabels = presentation.first;
    myWidth = presentation.second;
  }

  private @NotNull Pair<List<Pair<String, Icon>>, Integer> calculatePresentation(@NotNull List<? extends RefGroup> refGroups,
                                                                                 @NotNull List<VcsBookmarkRef> bookmarks,
                                                                                 @NotNull FontMetrics fontMetrics,
                                                                                 @NotNull Color background,
                                                                                 int availableWidth,
                                                                                 boolean compact) {
    if (compact) return calculateCompactPresentation(refGroups, bookmarks, fontMetrics, background, availableWidth);
    return calculateLongPresentation(refGroups, bookmarks, fontMetrics, background, availableWidth);
  }


  private @NotNull Pair<List<Pair<String, Icon>>, Integer> calculateCompactPresentation(@NotNull List<? extends RefGroup> refGroups,
                                                                                        @NotNull List<VcsBookmarkRef> bookmarks,
                                                                                        @NotNull FontMetrics fontMetrics,
                                                                                        @NotNull Color background,
                                                                                        int availableWidth) {
    List<Pair<String, Icon>> labels = new ArrayList<>();
    int width = LEFT_PADDING.get() + RIGHT_PADDING.get();
    int height = fontMetrics.getHeight();

    for (VcsBookmarkRef bookmark : bookmarks) {
      Icon icon = new BookmarkIcon(myComponent, height, background, bookmark);
      boolean isLast = refGroups.isEmpty() && bookmark == ContainerUtil.getLastItem(bookmarks);
      int newWidth = width + icon.getIconWidth() + (isLast ? 0 : COMPACT_MIDDLE_PADDING.get());
      newWidth += getIconTextPadding();

      labels.add(Pair.create("", icon));
      width = newWidth;
    }

    for (RefGroup group : refGroups) {
      List<Color> colors = group.getColors();
      LabelIcon labelIcon = getIcon(height, background, colors);
      boolean isLast = group == ContainerUtil.getLastItem(refGroups);
      int newWidth = width + labelIcon.getIconWidth() + (isLast ? 0 : COMPACT_MIDDLE_PADDING.get());

      String text = shortenRefName(group.getName(), fontMetrics, availableWidth - newWidth);
      newWidth += fontMetrics.stringWidth(text);
      newWidth += getIconTextPadding();

      labels.add(Pair.create(text, labelIcon));
      width = newWidth;
    }

    return Pair.create(labels, width);
  }

  private @NotNull Pair<List<Pair<String, Icon>>, Integer> calculateLongPresentation(@NotNull List<? extends RefGroup> refGroups,
                                                                                     @NotNull List<VcsBookmarkRef> bookmarks,
                                                                                     @NotNull FontMetrics fontMetrics,
                                                                                     @NotNull Color background,
                                                                                     int availableWidth) {
    List<Pair<String, Icon>> labels = new ArrayList<>();
    int width = LEFT_PADDING.get() + RIGHT_PADDING.get();
    int height = fontMetrics.getHeight();

    for (VcsBookmarkRef bookmark : bookmarks) {
      Icon icon = new BookmarkIcon(myComponent, height, background, bookmark);
      boolean isLast = refGroups.isEmpty() && bookmark == ContainerUtil.getLastItem(bookmarks);
      int newWidth = width + icon.getIconWidth() + (isLast ? 0 : MIDDLE_PADDING.get());
      labels.add(Pair.create("", icon));
      width = newWidth;
    }

    for (int i = 0; i < refGroups.size(); i++) {
      RefGroup group = refGroups.get(i);

      int doNotFitWidth = 0;
      if (i < refGroups.size() - 1) {
        LabelIcon lastIcon = getIcon(height, background, getColors(refGroups.subList(i + 1, refGroups.size())));
        doNotFitWidth = lastIcon.getIconWidth();
      }

      List<Color> colors = group.getColors();
      LabelIcon labelIcon = getIcon(height, background, colors);
      boolean isLast = i == refGroups.size() - 1;
      int newWidth = width + labelIcon.getIconWidth() + (isLast ? 0 : MIDDLE_PADDING.get());

      String text = getGroupText(group, fontMetrics, availableWidth - newWidth - doNotFitWidth);
      newWidth += fontMetrics.stringWidth(text);
      newWidth += getIconTextPadding();

      if (availableWidth - newWidth - doNotFitWidth < 0) {
        LabelIcon lastIcon = getIcon(height, background, getColors(refGroups.subList(i, refGroups.size())));
        String name = labels.isEmpty() ? text : "";
        labels.add(Pair.create(name, lastIcon));
        width += fontMetrics.stringWidth(name) + lastIcon.getIconWidth();
        break;
      }
      else {
        labels.add(Pair.create(text, labelIcon));
        width = newWidth;
      }
    }

    return Pair.create(labels, width);
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
    if (!group.isExpanded()) {
      return shortenRefName(group.getName(), fontMetrics, availableWidth);
    }

    StringBuilder text = new StringBuilder();
    String remainder = ", ...";
    String separator = ", ";
    int remainderWidth = fontMetrics.stringWidth(remainder);
    int separatorWidth = fontMetrics.stringWidth(separator);
    for (int i = 0; i < group.getRefs().size(); i++) {
      boolean lastRef = i == group.getRefs().size() - 1;
      boolean firstRef = i == 0;
      int width = availableWidth - (lastRef ? 0 : remainderWidth) - (firstRef ? 0 : separatorWidth);
      String refName = shortenRefName(group.getRefs().get(i).getName(), fontMetrics, width);
      int refNameWidth = fontMetrics.stringWidth(refName);
      if (width - refNameWidth < 0 && !firstRef) {
        text.append(remainder);
        break;
      }
      else {
        text.append(firstRef ? "" : separator).append(refName);
        availableWidth -= (firstRef ? 0 : separatorWidth) + refNameWidth;
      }
    }
    return text.toString();
  }

  private static @Nullable Color calculateGreyBackground(@NotNull List<? extends RefGroup> refGroups,
                                                         @NotNull Color background,
                                                         boolean isSelected,
                                                         boolean isCompact) {
    if (isSelected) return null;
    if (!isCompact) return getBranchPresentationBackground(background);

    boolean paintGreyBackground;
    for (RefGroup group : refGroups) {
      if (group.isExpanded()) {
        paintGreyBackground = ContainerUtil.find(group.getRefs(), ref -> !ref.getName().isEmpty()) != null;
      }
      else {
        paintGreyBackground = !group.getName().isEmpty();
      }

      if (paintGreyBackground) return getBranchPresentationBackground(background);
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

    for (Pair<String, Icon> label : myLabels) {
      Icon icon = label.second;
      String text = label.first;

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
      g2.setColor(myForeground);
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
}

