// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBValue;
import com.intellij.util.ui.JBValue.JBValueGroup;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.VcsLogRefManager;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogData;
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
  public static final JBValue LABEL_ARC = JBVG.value(6);
  private static final int MAX_LENGTH = 22;
  private static final String THREE_DOTS = "...";
  private static final String TWO_DOTS = "..";
  private static final String SEPARATOR = "/";
  private static final JBColor TEXT_COLOR = CurrentBranchComponent.TEXT_COLOR;

  @NotNull private final VcsLogData myLogData;
  @NotNull private final JComponent myComponent;
  @NotNull private final LabelIconCache myIconCache;

  @NotNull private List<Pair<String, LabelIcon>> myLabels = new ArrayList<>();
  private int myHeight = JBUIScale.scale(22);
  private int myWidth = 0;
  @NotNull private Color myBackground = UIUtil.getTableBackground();
  @Nullable private Color myGreyBackground = null;
  @NotNull private Color myForeground = UIUtil.getTableForeground();

  private boolean myCompact;
  private boolean myShowTagNames;

  public LabelPainter(@NotNull VcsLogData data,
                      @NotNull JComponent component,
                      @NotNull LabelIconCache iconCache,
                      boolean compact,
                      boolean showTagNames) {
    myLogData = data;
    myComponent = component;
    myIconCache = iconCache;
    myCompact = compact;
    myShowTagNames = showTagNames;
  }

  @Nullable
  public static VcsLogRefManager getRefManager(@NotNull VcsLogData logData, @NotNull Collection<? extends VcsRef> references) {
    if (!references.isEmpty()) {
      VirtualFile root = ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(references)).getRoot();
      return logData.getLogProvider(root).getReferenceManager();
    }
    else {
      return null;
    }
  }

  public void customizePainter(@NotNull Collection<? extends VcsRef> references,
                               @NotNull Color background,
                               @NotNull Color foreground,
                               boolean isSelected,
                               int availableWidth) {
    myBackground = background;
    myForeground = isSelected ? foreground : TEXT_COLOR;

    FontMetrics metrics = myComponent.getFontMetrics(getReferenceFont());
    myHeight = metrics.getHeight() + TOP_TEXT_PADDING.get() + BOTTOM_TEXT_PADDING.get();

    VcsLogRefManager manager = getRefManager(myLogData, references);
    List<RefGroup> refGroups = manager == null ? ContainerUtil.emptyList() : manager.groupForTable(references, myCompact, myShowTagNames);

    myGreyBackground = calculateGreyBackground(refGroups, background, isSelected, myCompact);
    Pair<List<Pair<String, LabelIcon>>, Integer> presentation =
      calculatePresentation(refGroups, metrics, myGreyBackground != null ? myGreyBackground : myBackground,
                            availableWidth, myCompact);

    myLabels = presentation.first;
    myWidth = presentation.second;
  }

  @NotNull
  private Pair<List<Pair<String, LabelIcon>>, Integer> calculatePresentation(@NotNull List<? extends RefGroup> refGroups,
                                                                             @NotNull FontMetrics fontMetrics,
                                                                             @NotNull Color background,
                                                                             int availableWidth,
                                                                             boolean compact) {
    int width = LEFT_PADDING.get() + RIGHT_PADDING.get();

    List<Pair<String, LabelIcon>> labels = new ArrayList<>();
    if (refGroups.isEmpty()) return Pair.create(labels, width);

    if (compact) return calculateCompactPresentation(refGroups, fontMetrics, background, availableWidth);
    return calculateLongPresentation(refGroups, fontMetrics, background, availableWidth);
  }


  @NotNull
  private Pair<List<Pair<String, LabelIcon>>, Integer> calculateCompactPresentation(@NotNull List<? extends RefGroup> refGroups,
                                                                                    @NotNull FontMetrics fontMetrics,
                                                                                    @NotNull Color background,
                                                                                    int availableWidth) {
    int width = LEFT_PADDING.get() + RIGHT_PADDING.get();

    List<Pair<String, LabelIcon>> labels = new ArrayList<>();
    if (refGroups.isEmpty()) return Pair.create(labels, width);

    for (RefGroup group : refGroups) {
      List<Color> colors = group.getColors();
      LabelIcon labelIcon = getIcon(fontMetrics.getHeight(), background, colors);
      int newWidth = width + labelIcon.getIconWidth() + (group != ContainerUtil.getLastItem(refGroups) ? COMPACT_MIDDLE_PADDING.get() : 0);

      String text = shortenRefName(group.getName(), fontMetrics, availableWidth - newWidth);
      newWidth += fontMetrics.stringWidth(text);

      labels.add(Pair.create(text, labelIcon));
      width = newWidth;
    }

    return Pair.create(labels, width);
  }

  @NotNull
  private Pair<List<Pair<String, LabelIcon>>, Integer> calculateLongPresentation(@NotNull List<? extends RefGroup> refGroups,
                                                                                 @NotNull FontMetrics fontMetrics,
                                                                                 @NotNull Color background,
                                                                                 int availableWidth) {
    int width = LEFT_PADDING.get() + RIGHT_PADDING.get();

    List<Pair<String, LabelIcon>> labels = new ArrayList<>();
    if (refGroups.isEmpty()) return Pair.create(labels, width);

    int height = fontMetrics.getHeight();
    for (int i = 0; i < refGroups.size(); i++) {
      RefGroup group = refGroups.get(i);

      int doNotFitWidth = 0;
      if (i < refGroups.size() - 1) {
        LabelIcon lastIcon = getIcon(height, background, getColors(refGroups.subList(i + 1, refGroups.size())));
        doNotFitWidth = lastIcon.getIconWidth();
      }

      List<Color> colors = group.getColors();
      LabelIcon labelIcon = getIcon(height, background, colors);
      int newWidth = width + labelIcon.getIconWidth() + (i != refGroups.size() - 1 ? MIDDLE_PADDING.get() : 0);

      String text = getGroupText(group, fontMetrics, availableWidth - newWidth - doNotFitWidth);
      newWidth += fontMetrics.stringWidth(text);

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

  @NotNull
  private LabelIcon getIcon(int height, @NotNull Color background, @NotNull List<? extends Color> colors) {
    return myIconCache.getIcon(myComponent, height, background, colors);
  }

  @NotNull
  private static List<Color> getColors(@NotNull Collection<? extends RefGroup> groups) {
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

  @NotNull
  private static String getGroupText(@NotNull RefGroup group, @NotNull FontMetrics fontMetrics, int availableWidth) {
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

  @Nullable
  private static Color calculateGreyBackground(@NotNull List<? extends RefGroup> refGroups,
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

  @NotNull
  private static String shortenRefName(@NotNull String refName, @NotNull FontMetrics fontMetrics, int availableWidth) {
    if (fontMetrics.stringWidth(refName) > availableWidth && refName.length() > MAX_LENGTH) {
      int separatorIndex = refName.indexOf(SEPARATOR);
      if (separatorIndex > TWO_DOTS.length()) {
        refName = TWO_DOTS + refName.substring(separatorIndex);
      }

      if (fontMetrics.stringWidth(refName) <= availableWidth) return refName;

      if (availableWidth > 0) {
        for (int i = refName.length(); i > MAX_LENGTH; i--) {
          String result = StringUtil.shortenTextWithEllipsis(refName, i, 0, THREE_DOTS);
          if (fontMetrics.stringWidth(result) <= availableWidth) {
            return result;
          }
        }
      }
      return StringUtil.shortenTextWithEllipsis(refName, MAX_LENGTH, 0, THREE_DOTS);
    }
    return refName;
  }

  public void paint(@NotNull Graphics2D g2, int x, int y, int height) {
    if (myLabels.isEmpty()) return;

    GraphicsConfig config = GraphicsUtil.setupAAPainting(g2);
    g2.setFont(getReferenceFont());
    g2.setStroke(new BasicStroke(1.5f));

    FontMetrics fontMetrics = g2.getFontMetrics();
    int baseLine = SimpleColoredComponent.getTextBaseLine(fontMetrics, height);

    g2.setColor(myBackground);
    g2.fillRect(x, y, myWidth, height);

    if (myGreyBackground != null && myCompact) {
      g2.setColor(myGreyBackground);
      g2.fillRect(x, y + baseLine - fontMetrics.getAscent() - TOP_TEXT_PADDING.get(),
                  myWidth,
                  fontMetrics.getHeight() + TOP_TEXT_PADDING.get() + BOTTOM_TEXT_PADDING.get());
    }

    x += LEFT_PADDING.get();

    for (Pair<String, LabelIcon> label : myLabels) {
      LabelIcon icon = label.second;
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

      g2.setColor(myForeground);
      g2.drawString(text, x, y + baseLine);
      x += fontMetrics.stringWidth(text) + (myCompact ? COMPACT_MIDDLE_PADDING.get() : MIDDLE_PADDING.get());
    }

    config.restore();
  }

  public Dimension getSize() {
    if (myLabels.isEmpty()) return new Dimension();
    return new Dimension(myWidth, myHeight);
  }

  public boolean isLeftAligned() {
    return Registry.is("vcs.log.labels.left.aligned");
  }

  public static Font getReferenceFont() {
    Font font = GraphCommitCellRenderer.getLabelFont();
    return font.deriveFont(font.getSize() - 1f);
  }

  public boolean isCompact() {
    return myCompact;
  }

  public void setShowTagNames(boolean showTagNames) {
    myShowTagNames = showTagNames;
  }

  public void setCompact(boolean compact) {
    myCompact = compact;
  }
}

