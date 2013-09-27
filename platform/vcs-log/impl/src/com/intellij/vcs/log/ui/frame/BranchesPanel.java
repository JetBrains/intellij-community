package com.intellij.vcs.log.ui.frame;

import com.google.common.collect.Ordering;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefManager;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.graph.render.PrintParameters;
import com.intellij.vcs.log.ui.VcsLogUI;
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

  @NotNull
  private List<VcsRef> getRefsToDisplayOnPanel() {
    Collection<VcsRef> allRefs = myDataHolder.getDataPack().getRefsModel().getAllRefs();

    List<VcsRef> refsToShow = new ArrayList<VcsRef>();
    for (Map.Entry<VirtualFile, Collection<VcsRef>> entry : groupByRoot(allRefs).entrySet()) {
      VirtualFile root = entry.getKey();
      Collection<VcsRef> refs = entry.getValue();
      VcsLogProvider provider = myDataHolder.getLogProvider(root);
      VcsLogRefManager refManager = provider.getReferenceManager();
      List<RefGroup> groups = refManager.group(refs);

      // TODO draw groups
      for (RefGroup group : groups) {
        if (group.getRefs().size() == 1) {
          refsToShow.add(group.getRefs().iterator().next());
        }
      }
    }
    // TODO improve UI for multiple roots case
    return refsToShow;
  }

  @NotNull
  private static MultiMap<VirtualFile, VcsRef> groupByRoot(@NotNull Collection<VcsRef> refs) {
    MultiMap<VirtualFile, VcsRef> map = new MultiMap<VirtualFile, VcsRef>() {
      @Override
      protected Map<VirtualFile, Collection<VcsRef>> createMap() {
        return new TreeMap<VirtualFile, Collection<VcsRef>>(new Comparator<VirtualFile>() { // TODO common to VCS root sorting method
          @Override
          public int compare(VirtualFile o1, VirtualFile o2) {
            return o1.getPresentableUrl().compareTo(o2.getPresentableUrl());
          }
        });
      }
    };
    for (VcsRef ref : refs) {
      map.putValue(ref.getRoot(), ref);
    }
    return map;
  }

}
