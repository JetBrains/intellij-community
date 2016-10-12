package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogRefs;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VisiblePack;
import com.intellij.vcs.log.impl.SingletonRefGroup;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.render.VcsRefPainter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * Panel with branch labels, above the graph.
 */
public class BranchesPanel extends JPanel {
  private static final int SMALL_ROOTS_GAP = 3;
  private static final int BIG_ROOTS_GAP = 7;
  private static final int TOP = 2;
  private static final int BOTTOM = 3;
  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogUiImpl myUi;
  @NotNull private final VcsRefPainter myReferencePainter;
  @NotNull private final JBScrollPane myScrollPane;

  @NotNull private LinkedHashMap<VirtualFile, List<RefGroup>> myRefGroups;
  @Nullable private Collection<VirtualFile> myRoots = null;

  public BranchesPanel(@NotNull VcsLogData logData, @NotNull VcsLogUiImpl ui, @NotNull VcsLogRefs initialRefsModel) {
    super(new FlowLayout(FlowLayout.LEADING, BIG_ROOTS_GAP - 2 * SMALL_ROOTS_GAP, 0));
    setBorder(new EmptyBorder(TOP, SMALL_ROOTS_GAP, BOTTOM, SMALL_ROOTS_GAP));

    myLogData = logData;
    myUi = ui;
    myRefGroups = getRefsToDisplayOnPanel(initialRefsModel);
    myReferencePainter = new VcsRefPainter(myUi.getColorManager(), true);
    myScrollPane =
      new JBScrollPane(this, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
    myScrollPane.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 0));
    myScrollPane.getHorizontalScrollBar().setUnitIncrement(10);
    myScrollPane.setBorder(BorderFactory.createEmptyBorder());

    recreateComponents();
  }

  @NotNull
  public JComponent getMainComponent() {
    return myScrollPane;
  }

  private void recreateComponents() {
    for (Map.Entry<VirtualFile, List<RefGroup>> entry : myRefGroups.entrySet()) {
      if (myRoots == null || myRoots.contains(entry.getKey())) {
        add(new RootGroupComponent(entry.getValue(), entry.getKey()));
      }
    }
  }

  public void rebuild(@NotNull VcsLogRefs refsModel) {
    myRefGroups = getRefsToDisplayOnPanel(refsModel);
    removeAll();
    recreateComponents();
    getParent().validate();
  }

  @NotNull
  private LinkedHashMap<VirtualFile, List<RefGroup>> getRefsToDisplayOnPanel(@NotNull VcsLogRefs refsModel) {
    Collection<VcsRef> allRefs = refsModel.getBranches();

    LinkedHashMap<VirtualFile, List<RefGroup>> groups = ContainerUtil.newLinkedHashMap();
    for (Map.Entry<VirtualFile, Set<VcsRef>> entry : VcsLogUtil.groupRefsByRoot(allRefs).entrySet()) {
      groups.put(entry.getKey(),
                 expandExpandableGroups(myLogData.getLogProvider(entry.getKey()).getReferenceManager().group(entry.getValue())));
    }

    return groups;
  }

  @NotNull
  private static List<RefGroup> expandExpandableGroups(@NotNull List<RefGroup> refGroups) {
    List<RefGroup> groups = ContainerUtil.newArrayList();
    for (RefGroup group : refGroups) {
      if (group.isExpanded() || group.getRefs().size() == 1) {
        groups.addAll(ContainerUtil.map(group.getRefs(), (Function<VcsRef, RefGroup>)ref -> new SingletonRefGroup(ref)));
      }
      else {
        groups.add(group);
      }
    }
    return groups;
  }

  public void onFiltersChange(@NotNull VcsLogFilterCollection filters) {
    myRoots = VcsLogUtil.getAllVisibleRoots(myLogData.getRoots(), filters.getRootFilter(), filters.getStructureFilter());
    removeAll();
    recreateComponents();
    getParent().validate();
  }

  public void updateDataPack(@NotNull VisiblePack dataPack, boolean permGraphChanged) {
    if (permGraphChanged) {
      rebuild(dataPack.getRefs());
    }
  }

  public void setBranchPanelVisible(boolean visible) {
    myScrollPane.setVisible(visible);
  }

  private class RootGroupComponent extends JPanel {
    @NotNull private final List<RefGroup> myGroups;

    private RootGroupComponent(@NotNull List<RefGroup> groups, @NotNull VirtualFile root) {
      super(new FlowLayout(FlowLayout.LEADING, SMALL_ROOTS_GAP, 0));
      myGroups = groups;

      for (RefGroup group : myGroups) {
        add(new ReferenceGroupComponent(group, myReferencePainter, myUi, root));
      }
    }
  }

  private static class ReferenceGroupComponent extends JPanel {
    @NotNull private final RefGroup myGroup;
    @NotNull private final VcsRefPainter myReferencePainter;
    @NotNull private final VcsLogUiImpl myUi;
    @NotNull private final VirtualFile myRoot;

    private ReferenceGroupComponent(@NotNull RefGroup group,
                                    @NotNull VcsRefPainter referencePainter,
                                    @NotNull VcsLogUiImpl ui,
                                    @NotNull VirtualFile root) {
      myGroup = group;
      myReferencePainter = referencePainter;
      myUi = ui;
      myRoot = root;
      addMouseListener(new MyMouseAdapter());
    }

    @Override
    protected void paintComponent(Graphics g) {
      Color rootIndicatorColor = VcsLogColorManagerImpl.getIndicatorColor(myUi.getColorManager().getRootColor(myRoot));
      myReferencePainter
        .paint(myGroup.getName(), g, 0, (getHeight() - myReferencePainter.getHeight(this)) / 2, myGroup.getBgColor(), rootIndicatorColor);
    }

    @Override
    public Dimension getPreferredSize() {
      return myReferencePainter.getSize(myGroup.getName(), this);
    }

    @Override
    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    private class MyMouseAdapter extends MouseAdapter {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (!myUi.areGraphActionsEnabled()) {
          return;
        }

        if (myGroup.getRefs().size() == 1) {
          VcsLogUtil.triggerUsage("BranchPanelGoToRef");

          VcsRef ref = myGroup.getRefs().iterator().next();
          myUi.jumpToCommit(ref.getCommitHash(), ref.getRoot());
        }
        else {
          VcsLogUtil.triggerUsage("BranchPanelPopup");

          ReferencePopupBuilder popupBuilder = new ReferencePopupBuilder(myGroup, myUi);
          popupBuilder.getPopup().showUnderneathOf(ReferenceGroupComponent.this);
        }
      }
    }
  }
}
