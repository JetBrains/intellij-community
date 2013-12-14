package com.intellij.vcs.log.ui.frame;

import com.google.common.collect.Ordering;
import com.intellij.openapi.project.Project;
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
import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefManager;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.impl.SingletonRefGroup;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogUI;
import com.intellij.vcs.log.ui.render.RefPainter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.vcs.log.graph.render.PrintParameters.HEIGHT_CELL;

/**
 * Panel with branch labels, above the graph.
 */
public class BranchesPanel extends JPanel {

  private final VcsLogDataHolder myDataHolder;
  private final VcsLogUI myUI;

  private List<RefGroup> myRefGroups;
  private final RefPainter myRefPainter;

  private Map<Integer, RefGroup> myRefPositions = ContainerUtil.newHashMap();

  public BranchesPanel(@NotNull VcsLogDataHolder dataHolder, @NotNull VcsLogUI UI) {
    myDataHolder = dataHolder;
    myUI = UI;
    myRefGroups = getRefsToDisplayOnPanel();
    myRefPainter = new RefPainter(myUI.getColorManager(), true);

    setPreferredSize(new Dimension(-1, HEIGHT_CELL + UIUtil.DEFAULT_VGAP));

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
          final RefPopupComponent view = new RefPopupComponent(group, myUI, myRefPainter);
          JBPopup popup = view.getPopup();
          popup.show(new RelativePoint(BranchesPanel.this, new Point(e.getX(), BranchesPanel.this.getHeight())));
        }
      }
    });

    Project project = dataHolder.getProject();
    project.getMessageBus().connect(project).subscribe(VcsLogDataHolder.REFRESH_COMPLETED, new Runnable() {
      @Override
      public void run() {
        rebuild();
      }
    });
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
    int paddingX = 0;
    for (RefGroup group : myRefGroups) {
      // TODO it is assumed here that all refs in a single group belong to a single root
      Color rootIndicatorColor = myUI.getColorManager().getRootColor(group.getRefs().iterator().next().getRoot());
      Rectangle rectangle = myRefPainter.drawLabel((Graphics2D)g, group.getName(), paddingX, group.getBgColor(), rootIndicatorColor);
      paddingX += rectangle.width + UIUtil.DEFAULT_HGAP;
      myRefPositions.put(rectangle.x, group);
    }
  }

  public void rebuild() {
    myRefGroups = getRefsToDisplayOnPanel();
    getParent().repaint();
  }

  @NotNull
  private List<RefGroup> getRefsToDisplayOnPanel() {
    Collection<VcsRef> allRefs = myDataHolder.getDataPack().getRefsModel().getBranches();

    List<RefGroup> groups = ContainerUtil.newArrayList();
    for (Map.Entry<VirtualFile, Collection<VcsRef>> entry : VcsLogUtil.groupRefsByRoot(allRefs).entrySet()) {
      VirtualFile root = entry.getKey();
      Collection<VcsRef> refs = entry.getValue();
      VcsLogProvider provider = myDataHolder.getLogProvider(root);
      VcsLogRefManager refManager = provider.getReferenceManager();
      groups.addAll(expandExpandableGroups(refManager.group(refs)));
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

  private static class RefPopupComponent extends JPanel {

    private final JBPopup myPopup;
    private final JBList myList;
    private final VcsLogUI myUi;
    private final RefPainter myRefPainter;

    private final SingleRefComponent myRendererComponent;

    private final ListCellRenderer myCellRenderer;

    RefPopupComponent(RefGroup group, VcsLogUI ui, RefPainter refPainter) {
      super(new BorderLayout());
      myUi = ui;
      myRefPainter = refPainter;

      myRendererComponent = new SingleRefComponent(myRefPainter);
      myCellRenderer = new ListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          myRendererComponent.setRef((VcsRef)value);
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

  private static class SingleRefComponent extends JPanel {

    private final RefPainter myRefPainter;

    private VcsRef myRef;
    public boolean mySelected;

    public SingleRefComponent(RefPainter refPainter) {
      myRefPainter = refPainter;
    }

    @Override
    protected void paintComponent(Graphics g) {
      g.setColor(mySelected ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground());
      g.fillRect(0, 0, getWidth(), getHeight());

      if (myRef != null) {
        myRefPainter.draw((Graphics2D)g, Collections.singletonList(myRef), 0, calcWidth());
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(calcWidth(), HEIGHT_CELL);
    }

    private int calcWidth() {
      FontMetrics metrics = getFontMetrics(getFont());
      return metrics.stringWidth(myRef.getName());
    }

    public void setRef(@NotNull VcsRef ref) {
      myRef = ref;
    }

    public void setSelected(boolean selected) {
      mySelected = selected;
    }
  }

}
