package com.intellij.coverage.view;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableScrollingUtil;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.components.JBList;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;

/**
 * User: anna
 * Date: 1/2/12
 */
public class CoverageView extends JPanel {
  @NonNls private static final String ACTION_DRILL_DOWN = "DrillDown";
  @NonNls private static final String ACTION_GO_UP = "GoUp";

  private CoverageTableModel myModel;
  private JBTable myTable;
  private CoverageViewBuilder myBuilder;
  private final Project myProject;
  private final CoverageViewManager.StateBean myStateBean;
  private final CoverageViewTreeStructure myStructure;

  public CoverageView(final Project project, final CoverageDataManager dataManager, CoverageViewManager.StateBean stateBean) {
    super(new BorderLayout());
    myProject = project;
    myStateBean = stateBean;
    final JLabel titleLabel = new JLabel();
    final CoverageSuitesBundle suitesBundle = dataManager.getCurrentSuitesBundle();
    myModel = new CoverageTableModel(suitesBundle.getAnnotator(project), dataManager);

    myTable = new JBTable(myModel);
    myTable.getColumnModel().getColumn(0).setCellRenderer(new NodeDescriptorTableCellRenderer());
    myTable.getTableHeader().setReorderingAllowed(false);
    JPanel centerPanel = new JPanel(new BorderLayout());
    centerPanel.add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
    centerPanel.add(titleLabel, BorderLayout.NORTH);
    add(centerPanel, BorderLayout.CENTER);
    myStructure = new CoverageViewTreeStructure(project, suitesBundle, stateBean);
    myBuilder = new CoverageViewBuilder(project, new JBList(), myModel, myStructure, null, myTable);
    myBuilder.setParentTitle(titleLabel);
    myBuilder.ensureSelectionExist();
    myBuilder.updateParentTitle();
    myTable.addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {
          drillDown(myStructure);
        }
      }
    });
    final TableSpeedSearch speedSearch = new TableSpeedSearch(myTable);
    speedSearch.setClearSearchOnNavigateNoMatch(true);

    TableScrollingUtil.installActions(myTable);

    myTable.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (myBuilder == null) return;
        myBuilder.buildRoot();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK), JComponent.WHEN_FOCUSED);

    myTable.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), ACTION_DRILL_DOWN);
    myTable.getInputMap(WHEN_FOCUSED).put(
      KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK), ACTION_DRILL_DOWN);
    myTable.getActionMap().put(ACTION_DRILL_DOWN, new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        drillDown(myStructure);
      }
    });
    myTable.getInputMap(WHEN_FOCUSED).put(
      KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK), ACTION_GO_UP);
    myTable.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), ACTION_GO_UP);
    myTable.getActionMap().put(ACTION_GO_UP, new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        goUp();
      }
    });

    final JComponent component =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, createToolbarActions(myStructure), false).getComponent();
    add(component, BorderLayout.WEST);
  }

  private ActionGroup createToolbarActions(final CoverageViewTreeStructure treeStructure) {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new GoUpAction(treeStructure));
    actionGroup.add(new FlattenPackagesAction());
    return actionGroup;
  }

  public void goUp() {
    if (myBuilder == null) {
      return;
    }
    myBuilder.goUp();
  }

  private void drillDown(CoverageViewTreeStructure treeStructure) {
    final int selectedRow = myTable.getSelectedRow();
    final AbstractTreeNode element = (AbstractTreeNode)myModel.getElementAt(selectedRow);
    if (treeStructure.getChildElements(element).length == 0) {
      if (element.canNavigate()) {
        element.navigate(true);
      }
      return;
    }
    myBuilder.drillDown();
  }

  private boolean topElementIsSelected(final CoverageViewTreeStructure treeStructure) {
    if (myTable == null) return false;
    int[] selectedIndices = myTable.getSelectedRows();
    if (selectedIndices.length >= 1) {
      final AbstractTreeNode rootElement = (AbstractTreeNode)treeStructure.getRootElement();
      final AbstractTreeNode node = (AbstractTreeNode)myModel.getElementAt(0);
      if (node.getParent() == rootElement) {
        return true;
      }
    }
    return false;
  }

  public boolean canSelect(VirtualFile file) {
    final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile instanceof PsiClassOwner) {
      final String packageName = ((PsiClassOwner)psiFile).getPackageName();
      return myStructure.contains(packageName);
    }
    return false;
  }

  public void select(VirtualFile file) {
    final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile instanceof PsiClassOwner) {
      myBuilder.selectElement(((PsiClassOwner)psiFile).getClasses()[0], file);
    }
  }

  private static class NodeDescriptorTableCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table,
                                                   Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus,
                                                   int row,
                                                   int column) {
      final Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (value instanceof NodeDescriptor) {
        NodeDescriptor descriptor = (NodeDescriptor)value;
        setIcon(descriptor.getOpenIcon());
        setText(descriptor.toString());
      }
      return component;
    }
  }

  private class FlattenPackagesAction extends ToggleAction {

    private FlattenPackagesAction() {
      super("Flatten Packages", "Flatten Packages", IconLoader.getIcon("/objectBrowser/flattenPackages.png"));
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myStateBean.myFlattenPackages;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myStateBean.myFlattenPackages = state;
      myBuilder.buildRoot();
    }
  }
  
  private class GoUpAction extends AnAction {

    private final CoverageViewTreeStructure myTreeStructure;

    public GoUpAction(CoverageViewTreeStructure treeStructure) {
      super("Go Up", "Go to Upper Level", IconLoader.getIcon("/nodes/upLevel.png"));
      myTreeStructure = treeStructure;
      registerCustomShortcutSet(KeyEvent.VK_BACK_SPACE, 0, myTable);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      goUp();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(!topElementIsSelected(myTreeStructure));
    }
  }
}
