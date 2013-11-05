package com.intellij.vcs.log.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.ui.tables.AbstractVcsLogTableModel;
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
    JBColor.RED, JBColor.YELLOW, JBColor.LIGHT_GRAY, JBColor.BLUE, JBColor.MAGENTA,
    JBColor.CYAN, JBColor.GREEN, JBColor.ORANGE, JBColor.PINK};

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
    for (VirtualFile root : roots) {
      Color color;
      if (i >= ROOT_COLORS.length) {
        color = getDefaultRootColor();
      }
      else {
        color = ROOT_COLORS[i];
        i++;
      }
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
    if (root == AbstractVcsLogTableModel.FAKE_ROOT) {
      return getDefaultRootColor();
    }
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
