package com.intellij.vcs.log.ui.frame;

import com.google.common.collect.Ordering;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ListUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
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
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
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
  private static final int VERTICAL_SPACE = 3;
  private final VcsLogDataHolder myDataHolder;
  private final VcsLogUiImpl myUI;

  private LinkedHashMap<VirtualFile, List<RefGroup>> myRefGroups;
  private final VcsRefPainter myReferencePainter;
  @Nullable private Collection<VirtualFile> myRoots = null;

  private Map<Integer, RefGroup> myRefPositions = ContainerUtil.newHashMap();

  public BranchesPanel(@NotNull VcsLogDataHolder dataHolder, @NotNull VcsLogUiImpl UI, @NotNull RefsModel initialRefsModel) {
    myDataHolder = dataHolder;
    myUI = UI;
    myRefGroups = getRefsToDisplayOnPanel(initialRefsModel);
    myReferencePainter = new VcsRefPainter(myUI.getColorManager(), true);

    setPreferredSize(new Dimension(-1, myReferencePainter.getHeight(this) + UIUtil.DEFAULT_VGAP + VERTICAL_SPACE));

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (!myUI.getMainFrame().areGraphActionsEnabled()) {
          return;
        }

        final RefGroup group = findRef(e);
        if (group == null) {
          return;
        }
        if (group.getRefs().size() == 1) {
          VcsRef ref = group.getRefs().iterator().next();
          myUI.jumpToCommit(ref.getCommitHash());
        }
        else {
          final ReferencePopupComponent view = new ReferencePopupComponent(group, myUI, myReferencePainter);
          JBPopup popup = view.getPopup();
          popup.show(new RelativePoint(BranchesPanel.this, new Point(e.getX(), BranchesPanel.this.getHeight())));
        }
      }
    });

    myUI.addLogListener(new VcsLogListener() {
      @Override
      public void onChange(@NotNull VcsLogDataPack dataPack, boolean refresh) {
        if (refresh) {
          rebuild(dataPack.getRefs());
        }
      }
    });
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(-1, myReferencePainter.getHeight(this) + 10);
  }

  @Nullable
  private RefGroup findRef(MouseEvent e) {
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
    myRefPositions = ContainerUtil.newHashMap();
    int paddingX = BIG_ROOTS_GAP;
    for (Map.Entry<VirtualFile, List<RefGroup>> entry : myRefGroups.entrySet()) {
      VirtualFile root = entry.getKey();
      for (RefGroup group : entry.getValue()) {
        // it is assumed here that all refs in a single group belong to a single root
        if (myRoots == null || myRoots.contains(root)) {
          Color rootIndicatorColor = VcsLogColorManagerImpl.getIndicatorColor(myUI.getColorManager().getRootColor(root));
          Rectangle rectangle = myReferencePainter
            .paint(group.getName(), g, paddingX, (getHeight() - myReferencePainter.getHeight(this)) / 2 - VERTICAL_SPACE,
                   group.getBgColor(),
                   rootIndicatorColor);
          paddingX += rectangle.width + SMALL_ROOTS_GAP;
          myRefPositions.put(rectangle.x, group);
        }
      }
      paddingX += BIG_ROOTS_GAP - SMALL_ROOTS_GAP;
    }
  }

  public void rebuild(@NotNull VcsLogRefs refsModel) {
    myRefGroups = getRefsToDisplayOnPanel(refsModel);
    getParent().repaint();
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
    getParent().repaint();
  }

  private static class ReferencePopupComponent extends JPanel {
    @NotNull private final JBPopup myPopup;
    @NotNull private final JBList myList;
    @NotNull private final VcsLogUiImpl myUi;
    @NotNull private final SingleReferenceComponent myRendererComponent;
    @NotNull private final ListCellRenderer myCellRenderer;

    ReferencePopupComponent(@NotNull RefGroup group, @NotNull VcsLogUiImpl ui, @NotNull VcsRefPainter referencePainter) {
      super(new BorderLayout());
      myUi = ui;

      myRendererComponent = new SingleReferenceComponent(referencePainter);
      myCellRenderer = new ListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          myRendererComponent.setReference((VcsRef)value);
          myRendererComponent.setSelected(isSelected);
          return myRendererComponent;
        }
      };

      myList = createList(group);
      myPopup = createPopup();
      add(new JBScrollPane(myList));
    }

    private JBList createList(RefGroup group) {
      JBList list = new JBList(createListModel(group));
      list.setCellRenderer(myCellRenderer);
      ListUtil.installAutoSelectOnMouseMove(list);
      list.setSelectedIndex(0);
      list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      jumpOnMouseClick(list);
      jumpOnEnter(list);

      return list;
    }

    private void jumpOnMouseClick(JBList list) {
      list.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          jumpToSelectedRef();
        }
      });
    }

    private void jumpOnEnter(JBList list) {
      list.addKeyListener(new KeyAdapter() {
        @Override
        public void keyTyped(KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            jumpToSelectedRef();
          }
        }
      });
    }

    private JBPopup createPopup() {
      return JBPopupFactory.getInstance().
        createComponentPopupBuilder(this, myList).
        setCancelOnClickOutside(true).
        setCancelOnWindowDeactivation(true).
        setFocusable(true).
        setRequestFocus(true).
        setResizable(true).
        setDimensionServiceKey(myUi.getProject(), "Vcs.Log.Branch.Panel.RefGroup.Popup", false).
        createPopup();
    }

    private static DefaultListModel createListModel(RefGroup group) {
      DefaultListModel model = new DefaultListModel();
      for (final VcsRef vcsRef : group.getRefs()) {
        model.addElement(vcsRef);
      }
      return model;
    }

    @NotNull
    JBPopup getPopup() {
      return myPopup;
    }

    private void jumpToSelectedRef() {
      myPopup.cancel(); // close the popup immediately not to stay at the front if jumping to a commits takes long time.
      VcsRef selectedRef = (VcsRef)myList.getSelectedValue();
      if (selectedRef != null) {
        myUi.jumpToCommit(selectedRef.getCommitHash());
      }
    }
  }

  private static class SingleReferenceComponent extends JPanel {
    private static final int PADDING_Y = 2;
    private static final int PADDING_X = 5;
    @NotNull private final VcsRefPainter myReferencePainter;

    @Nullable private VcsRef myReference;
    public boolean mySelected;

    public SingleReferenceComponent(@NotNull VcsRefPainter referencePainter) {
      myReferencePainter = referencePainter;
    }

    @Override
    protected void paintComponent(Graphics g) {
      g.setColor(mySelected ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground());
      g.fillRect(0, 0, getWidth(), getHeight());

      if (myReference != null) {
        myReferencePainter.paint(myReference, g, PADDING_X, PADDING_Y);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      if (myReference == null) return super.getPreferredSize();
      Dimension size = myReferencePainter.getSize(myReference, this);
      return new Dimension(size.width + 2 * PADDING_X, size.height + 2 * PADDING_Y);
    }

    public void setReference(@NotNull VcsRef reference) {
      myReference = reference;
    }

    public void setSelected(boolean selected) {
      mySelected = selected;
    }
  }

}
