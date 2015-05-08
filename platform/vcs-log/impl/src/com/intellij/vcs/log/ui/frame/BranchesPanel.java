package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ScrollUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.RefsModel;
import com.intellij.vcs.log.data.VcsLogDataHolder;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel with branch labels, above the graph.
 */
public class BranchesPanel extends JPanel {
  private static final int SMALL_ROOTS_GAP = 3;
  private static final int BIG_ROOTS_GAP = 7;
  private static final int TOP = 0;
  private static final int BOTTOM = 3;
  @NotNull private final VcsLogDataHolder myDataHolder;
  @NotNull private final VcsLogUiImpl myUI;
  @NotNull private final VcsRefPainter myReferencePainter;

  @NotNull private LinkedHashMap<VirtualFile, List<RefGroup>> myRefGroups;
  @Nullable private Collection<VirtualFile> myRoots = null;

  public BranchesPanel(@NotNull VcsLogDataHolder dataHolder, @NotNull VcsLogUiImpl UI, @NotNull RefsModel initialRefsModel) {
    super(new FlowLayout(FlowLayout.LEADING, BIG_ROOTS_GAP - 2 * SMALL_ROOTS_GAP, 0));
    setBorder(new EmptyBorder(TOP, SMALL_ROOTS_GAP, BOTTOM, SMALL_ROOTS_GAP));

    myDataHolder = dataHolder;
    myUI = UI;
    myRefGroups = getRefsToDisplayOnPanel(initialRefsModel);
    myReferencePainter = new VcsRefPainter(myUI.getColorManager(), true);

    recreateComponents();

    myUI.addLogListener(new VcsLogListener() {
      @Override
      public void onChange(@NotNull VcsLogDataPack dataPack, boolean refresh) {
        if (refresh) {
          rebuild(dataPack.getRefs());
        }
      }
    });
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
    for (Map.Entry<VirtualFile, Collection<VcsRef>> entry : VcsLogUtil.groupRefsByRoot(allRefs).entrySet()) {
      VirtualFile root = entry.getKey();
      List<RefGroup> list = ContainerUtil.newArrayList();
      list.addAll(expandExpandableGroups(myDataHolder.getLogProvider(root).getReferenceManager().group(entry.getValue())));
      groups.put(root, list);
    }

    return groups;
  }

  private static Collection<RefGroup> expandExpandableGroups(List<RefGroup> refGroups) {
    Collection<RefGroup> groups = ContainerUtil.newArrayList();
    for (RefGroup group : refGroups) {
      if (group.isExpanded()) {
        groups.addAll(ContainerUtil.map(group.getRefs(), new Function<VcsRef, RefGroup>() {
          @Override
          public RefGroup fun(VcsRef ref) {
            return new SingletonRefGroup(ref);
          }
        }));
      }
      else {
        groups.add(group);
      }
    }
    return groups;
  }

  public void onFiltersChange(@NotNull VcsLogFilterCollection filters) {
    myRoots = VcsLogUtil.getAllVisibleRoots(myDataHolder.getRoots(), filters.getRootFilter(), filters.getStructureFilter());
    removeAll();
    recreateComponents();
    getParent().validate();
  }

  public JComponent createScrollPane() {
    JBScrollPane scrollPane =
      new JBScrollPane(this, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
    scrollPane.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 0));
    scrollPane.getHorizontalScrollBar().setUnitIncrement(10);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    return scrollPane;
  }

  private class RootGroupComponent extends JPanel {
    @NotNull private final List<RefGroup> myGroups;

    private RootGroupComponent(@NotNull List<RefGroup> groups, @NotNull VirtualFile root) {
      super(new FlowLayout(FlowLayout.LEADING, SMALL_ROOTS_GAP, 0));
      myGroups = groups;

      for (RefGroup group : myGroups) {
        add(new ReferenceGroupComponent(group, myReferencePainter, myUI, root));
      }
    }
  }

  private static class ReferenceGroupComponent extends JPanel {
    @NotNull private final RefGroup myGroup;
    @NotNull private final VcsRefPainter myReferencePainter;
    @NotNull private final VcsLogUiImpl myUI;
    @NotNull private final VirtualFile myRoot;

    private ReferenceGroupComponent(@NotNull RefGroup group,
                                    @NotNull VcsRefPainter referencePainter,
                                    @NotNull VcsLogUiImpl ui,
                                    @NotNull VirtualFile root) {
      myGroup = group;
      myReferencePainter = referencePainter;
      myUI = ui;
      myRoot = root;
      addMouseListener(new MyMouseAdapter());
    }

    @Override
    protected void paintComponent(Graphics g) {
      Color rootIndicatorColor = VcsLogColorManagerImpl.getIndicatorColor(myUI.getColorManager().getRootColor(myRoot));
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
        if (!myUI.getMainFrame().areGraphActionsEnabled()) {
          return;
        }

        if (myGroup.getRefs().size() == 1) {
          VcsRef ref = myGroup.getRefs().iterator().next();
          myUI.jumpToCommit(ref.getCommitHash(), ref.getRoot());
        }
        else {
          final ReferencePopupComponent view = new ReferencePopupComponent(myGroup, myUI, myReferencePainter);
          JBPopup popup = view.getPopup();
          popup.show(new RelativePoint(ReferenceGroupComponent.this, new Point(e.getX(), ReferenceGroupComponent.this.getHeight())));
        }
      }
    }
  }
}
