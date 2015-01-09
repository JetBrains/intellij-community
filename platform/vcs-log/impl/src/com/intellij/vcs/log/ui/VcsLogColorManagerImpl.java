package com.intellij.vcs.log.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class VcsLogColorManagerImpl implements VcsLogColorManager {

  private static final Color REF_BORDER = JBColor.GRAY;
  private static final Color ROOT_INDICATOR_BORDER = JBColor.LIGHT_GRAY;
  private static final Logger LOG = Logger.getInstance(VcsLogColorManagerImpl.class);

  private static Color[] ROOT_COLORS = {
    JBColor.RED, JBColor.GREEN, JBColor.BLUE,
    JBColor.ORANGE, JBColor.CYAN, JBColor.YELLOW,
    JBColor.MAGENTA, JBColor.PINK};

  @NotNull private final List<VirtualFile> myRoots;

  @NotNull private final Map<VirtualFile, Color> myRoots2Colors;

  public VcsLogColorManagerImpl(@NotNull Collection<VirtualFile> roots) {
    myRoots = new ArrayList<VirtualFile>(roots);
    Collections.sort(myRoots, new Comparator<VirtualFile>() { // TODO add a common util method to sort roots
      @Override
      public int compare(VirtualFile o1, VirtualFile o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    myRoots2Colors = ContainerUtil.newHashMap();
    int i = 0;
    for (VirtualFile root : myRoots) {
      Color color;
      if (i >= ROOT_COLORS.length) {
        double balance = ((double)(i / ROOT_COLORS.length)) / (roots.size() / ROOT_COLORS.length);
        Color mix = ColorUtil.mix(ROOT_COLORS[i % ROOT_COLORS.length], ROOT_COLORS[(i + 1) % ROOT_COLORS.length], balance);
        int tones = (int)(Math.abs(balance - 0.5) * 2 * (roots.size() / ROOT_COLORS.length) + 1);
        color = new JBColor(ColorUtil.darker(mix, tones), ColorUtil.brighter(mix, 2 * tones));
      }
      else {
        color = ROOT_COLORS[i];
      }
      i++;
      myRoots2Colors.put(root, color);
    }
  }

  @Override
  public boolean isMultipleRoots() {
    return myRoots.size() > 1;
  }

  @NotNull
  @Override
  public Color getRootColor(@NotNull VirtualFile root) {
    Color color = myRoots2Colors.get(root);
    if (color == null) {
      LOG.error("No color record for root " + root + ". All roots: " + myRoots2Colors);
      color = getDefaultRootColor();
    }
    return color;
  }

  private static Color getDefaultRootColor() {
    return UIUtil.getTableBackground();
  }

  @NotNull
  @Override
  public Color getReferenceBorderColor() {
    return REF_BORDER;
  }

  @NotNull
  @Override
  public Color getRootIndicatorBorder() {
    return ROOT_INDICATOR_BORDER;
  }

}
