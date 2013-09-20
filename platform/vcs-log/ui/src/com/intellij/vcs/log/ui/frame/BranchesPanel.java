package com.intellij.vcs.log.ui.frame;

import com.google.common.collect.Ordering;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.ui.VcsLogUI;
import com.intellij.vcs.log.graph.render.PrintParameters;
import com.intellij.vcs.log.ui.render.RefPainter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * Panel with branch labels, above the graph.
 *
 * @author Kirill Likhodedov
 */
public class BranchesPanel extends JPanel {

  private final VcsLogDataHolder myDataHolder;
  private final VcsLogUI myUI;

  private List<VcsRef> myRefs;
  private final RefPainter myRefPainter;

  private Map<Integer, VcsRef> myRefPositions = new HashMap<Integer, VcsRef>();

  public BranchesPanel(@NotNull VcsLogDataHolder dataHolder, @NotNull VcsLogUI UI) {
    myDataHolder = dataHolder;
    myUI = UI;
    myRefs = getRefsToDisplayOnPanel();
    myRefPainter = new RefPainter(myUI.getColorManager(), true);

    setPreferredSize(new Dimension(-1, PrintParameters.HEIGHT_CELL + UIUtil.DEFAULT_VGAP));

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        VcsRef ref = findRef(e);
        if (ref != null) {
          myUI.jumpToCommit(ref.getCommitHash());
        }
      }
    });

  }

  @Nullable
  private VcsRef findRef(MouseEvent e) {
    List<Integer> sortedPositions = Ordering.natural().sortedCopy(myRefPositions.keySet());
    int index = Ordering.natural().binarySearch(sortedPositions, e.getX());
    if (index < 0) {
      index = -index - 2;
    }
    if (index < 0) {
      return null;
    }
    return myRefPositions.get(sortedPositions.get(index));
  }

  @Override
  protected void paintComponent(Graphics g) {
    myRefPositions = myRefPainter.draw((Graphics2D)g, myRefs, 0);
  }

  public void rebuild() {
    myRefs = getRefsToDisplayOnPanel();
    getParent().repaint();
  }

  private List<VcsRef> getRefsToDisplayOnPanel() {
    Collection<VcsRef> allRefs = myDataHolder.getDataPack().getRefsModel().getAllRefs();

    List<VcsRef> refsToShow = ContainerUtil.filter(allRefs, new Condition<VcsRef>() {
      @Override
      public boolean value(VcsRef ref) {
        if (ref.getType() == VcsRef.RefType.REMOTE_BRANCH) {
          return true;
        }
        if (ref.getType().isLocalOrHead()) {
          return true;
        }
        return false;
      }
    });

    // TODO sort roots as well
    // TODO improve UI for multiple roots case
    return sortReferences(refsToShow);
  }

  private List<VcsRef> sortReferences(List<VcsRef> refsToShow) {
    List<VcsRef> sortedRefs = new ArrayList<VcsRef>(refsToShow.size());
    MultiMap<VirtualFile, VcsRef> map = groupByRoot(refsToShow);
    for (Map.Entry<VirtualFile, Collection<VcsRef>> entry : map.entrySet()) {
      List<VcsRef> sortedRefsForRoot = myDataHolder.getLogProvider(entry.getKey()).getRefSorter().sort(entry.getValue());
      sortedRefs.addAll(sortedRefsForRoot);
    }
    return sortedRefs;
  }

  private static MultiMap<VirtualFile, VcsRef> groupByRoot(Collection<VcsRef> refs) {
    MultiMap<VirtualFile, VcsRef> map = MultiMap.create();
    for (VcsRef ref : refs) {
      map.putValue(ref.getRoot(), ref);
    }
    return map;
  }

}
