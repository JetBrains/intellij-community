package org.jetbrains.android.uipreview;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.JBDefaultTreeCellRenderer;
import com.intellij.ui.TableScrollingUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.treeStructure.treetable.*;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class LayoutDeviceConfigurationsDialog extends DialogWrapper {
  public static final String CONFIGURATION_COLUMN_NAME = "Configuration";
  public static final String CUSTOM_CATEGORY_NAME = "Custom";
  private final JPanel myContentPanel;
  private final TreeTable myTable;
  private final LayoutDeviceManager myLayoutDeviceManager;

  private final AnActionButton myEditButton;
  private final AnActionButton myRemoveButton;
  
  private String mySelectedDeviceConfigName;
  private String mySelectedDeviceName;

  private final Project myProject;
  private DefaultMutableTreeNode myCustomCategoryRoot;

  public LayoutDeviceConfigurationsDialog(@NotNull Project project,
                                             @Nullable LayoutDeviceConfiguration selectedConfig,
                                             @NotNull LayoutDeviceManager layoutDeviceManager) {
    super(project, true);

    myProject = project;
    myLayoutDeviceManager = layoutDeviceManager;

    if (selectedConfig != null) {
      mySelectedDeviceConfigName = selectedConfig.getName();
      mySelectedDeviceName = selectedConfig.getDevice().getName();
    }

    setTitle(AndroidBundle.message("android.layout.preview.device.configurations.dialog.title"));

    myTable = new TreeTable(new ListTreeTableModel(new DefaultMutableTreeNode(), ColumnInfo.EMPTY_ARRAY));

    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTable);

    AnActionButton addButton =
      new AnActionButton(AndroidBundle.message("android.layout.preview.device.configurations.dialog.add.button"), null,
                         IconUtil.getAddRowIcon()) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          doAdd();
        }
      };
    addButton.setShortcut(CustomShortcutSet.fromString("alt A", "INSERT"));

    myEditButton = new AnActionButton(AndroidBundle.message("android.layout.preview.device.configurations.dialog.edit.button"), null,
                                      IconUtil.getEditIcon()) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        doEdit();
      }
    };
    myEditButton.setShortcut(CustomShortcutSet.fromString("alt E"));

    myRemoveButton = new AnActionButton(AndroidBundle.message("android.layout.preview.device.configurations.dialog.remove.button"), null,
                                        IconUtil.getRemoveRowIcon()) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        doRemove();
      }
    };
    myRemoveButton.setShortcut(CustomShortcutSet.fromString("alt DELETE"));

    decorator.addExtraAction(addButton);
    decorator.addExtraAction(myEditButton);
    decorator.addExtraAction(myRemoveButton);

    myContentPanel = new JPanel(new BorderLayout());
    myContentPanel.add(decorator.createPanel(), BorderLayout.CENTER);
    myContentPanel.setPreferredSize(new Dimension(750, 750));

    updateTable(selectedConfig);

    final TreeTableTree tree = myTable.getTree();
    tree.setCellRenderer(new JBDefaultTreeCellRenderer(tree) {
      @Override
      public Component getTreeCellRendererComponent(JTree tree,
                                                    Object value,
                                                    boolean sel,
                                                    boolean expanded,
                                                    boolean leaf,
                                                    int row,
                                                    boolean hasFocus) {
        if (value != null) {
          final Object obj = ((DefaultMutableTreeNode)value).getUserObject();
          if (obj instanceof LayoutDevice) {
            value = ((LayoutDevice)obj).getName();
          }
          else if (obj instanceof LayoutDeviceConfiguration) {
            value = ((LayoutDeviceConfiguration)obj).getName();
          }
        }
        setLeafIcon(null);
        setOpenIcon(null);
        setClosedIcon(null);
        return super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      }
    });

    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    myTable.getColumn(CONFIGURATION_COLUMN_NAME).setPreferredWidth(400);
    myTable.setRowHeight(23);

    myTable.getColumn(CONFIGURATION_COLUMN_NAME).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        if (value instanceof LayoutDeviceConfiguration) {
          value = ((LayoutDeviceConfiguration)value).getConfiguration().toDisplayString();
        }
        return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      }
    });

    myTable.setRootVisible(false);
    tree.setShowsRootHandles(true);

    myTable.setEnableAntialiasing(true);

    updateButtons();
    init();
  }


  private void updateTable(LayoutDeviceConfiguration selection) {
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode();

    final List<DefaultMutableTreeNode> platformDeviceNodes = new ArrayList<DefaultMutableTreeNode>();
    final List<DefaultMutableTreeNode> addOnDeviceNodes = new ArrayList<DefaultMutableTreeNode>();
    final List<DefaultMutableTreeNode> customDeviceNodes = new ArrayList<DefaultMutableTreeNode>();

    final List<LayoutDevice> devices = myLayoutDeviceManager.getCombinedList();

    for (LayoutDevice device : devices) {
      final DefaultMutableTreeNode deviceNode = new DefaultMutableTreeNode(device);

      for (LayoutDeviceConfiguration config : device.getConfigurations()) {
        final DefaultMutableTreeNode child = new DefaultMutableTreeNode(config);
        deviceNode.add(child);
      }
      switch (device.getType()) {
        case PLATFORM:
          platformDeviceNodes.add(deviceNode);
          break;
        case ADD_ON:
          addOnDeviceNodes.add(deviceNode);
          break;
        case CUSTOM:
          customDeviceNodes.add(deviceNode);
          break;
      }
    }

    if (platformDeviceNodes.size() > 0) {
      final DefaultMutableTreeNode platformRoot = new DefaultMutableTreeNode("Platform");
      for (DefaultMutableTreeNode node : platformDeviceNodes) {
        platformRoot.add(node);
      }
      root.add(platformRoot);
    }

    if (addOnDeviceNodes.size() > 0) {
      final DefaultMutableTreeNode addOnRoot = new DefaultMutableTreeNode("Add-on");
      for (DefaultMutableTreeNode node : addOnDeviceNodes) {
        addOnRoot.add(node);
      }
      root.add(addOnRoot);
    }

    if (customDeviceNodes.size() > 0) {
      myCustomCategoryRoot = new DefaultMutableTreeNode(CUSTOM_CATEGORY_NAME);
      for (DefaultMutableTreeNode node : customDeviceNodes) {
        myCustomCategoryRoot.add(node);
      }
      root.add(myCustomCategoryRoot);
    }


    myTable.setModel(new ListTreeTableModelOnColumns(root, new ColumnInfo[]{new ColumnInfo("Name") {
      @Override
      public Class getColumnClass() {
        return TreeTableModel.class;
      }

      @Override
      public Object valueOf(Object o) {
        final Object object = ((DefaultMutableTreeNode)o).getUserObject();
        if (object instanceof LayoutDevice || object instanceof LayoutDeviceConfiguration) {
          return object;
        }
        return null;
      }
    }, new ColumnInfo(CONFIGURATION_COLUMN_NAME) {
      @Override
      public Object valueOf(Object o) {
        final Object object = ((DefaultMutableTreeNode)o).getUserObject();
        if (object instanceof LayoutDeviceConfiguration) {
          return object;
        }
        return null;
      }
    }}));

    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateButtons();
      }
    });

    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    TreeUtil.expandAll(myTable.getTree());

    if (selection != null) {
      final Pair<Integer, DefaultMutableTreeNode> pair = findObjectInTree(root, selection);
      if (pair != null) {
        selectRowInTableAndScroll(pair.first);
      }
    }
    else if (myCustomCategoryRoot != null) {
      final Pair<Integer, DefaultMutableTreeNode> pair = findObjectInTree(root, myCustomCategoryRoot.getUserObject());
      if (pair != null) {
        selectRowInTableAndScroll(pair.first);
      }
    }
  }

  private void selectRowInTable(final int row) {
    myTable.getSelectionModel().setSelectionInterval(row, row);
  }

  @Nullable
  private static Pair<Integer, DefaultMutableTreeNode> findObjectInTree(@NotNull TreeNode root, @NotNull Object o) {
    int rowIndex = 0;
    for (int i = 0; i < root.getChildCount(); i++) {
      final TreeNode categoryNode = root.getChildAt(i);
      if (categoryNode instanceof DefaultMutableTreeNode && o.equals(((DefaultMutableTreeNode)categoryNode).getUserObject())) {
        return new Pair<Integer, DefaultMutableTreeNode>(rowIndex, (DefaultMutableTreeNode)categoryNode);
      }

      rowIndex++;

      for (int j = 0; j < categoryNode.getChildCount(); j++) {
        final TreeNode deviceNode = categoryNode.getChildAt(j);
        if (deviceNode instanceof DefaultMutableTreeNode && o.equals(((DefaultMutableTreeNode)deviceNode).getUserObject())) {
          return new Pair<Integer, DefaultMutableTreeNode>(rowIndex, (DefaultMutableTreeNode)deviceNode);
        }

        rowIndex++;

        for (int k = 0; k < deviceNode.getChildCount(); k++) {
          final TreeNode configNode = deviceNode.getChildAt(k);
          if (configNode instanceof DefaultMutableTreeNode && o.equals(((DefaultMutableTreeNode)configNode).getUserObject())) {
            return new Pair<Integer, DefaultMutableTreeNode>(rowIndex, (DefaultMutableTreeNode)configNode);
          }
          rowIndex++;
        }
      }
    }
    return null;
  }

  private void doRemove() {
    final int selectedRow = myTable.getSelectedRow();
    final DefaultMutableTreeNode selectedNode = getSelectedNode();
    if (selectedNode == null) {
      return;
    }

    final Object o = selectedNode.getUserObject();
    if (!(o instanceof LayoutDevice || o instanceof LayoutDeviceConfiguration)) {
      return;
    }

    if (o instanceof LayoutDevice) {
      myLayoutDeviceManager.removeUserDevice((LayoutDevice)o);
    }
    else {
      final LayoutDeviceConfiguration config = (LayoutDeviceConfiguration)o;
      myLayoutDeviceManager.removeUserConfiguration(config.getDevice(), config.getName());
    }
    final TreeNode parent = selectedNode.getParent();
    selectedNode.removeFromParent();
    nodeStructureChanged(parent);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        myTable.getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
      }
    });
  }

  private void doEdit() {
    final DefaultMutableTreeNode selectedNode = getSelectedNode();
    assert selectedNode != null;

    final Object o = selectedNode.getUserObject();
    assert o instanceof LayoutDevice || o instanceof LayoutDeviceConfiguration;

    if (o instanceof LayoutDevice) {
      final LayoutDevice device = (LayoutDevice)o;
      final EditDeviceForm form = new EditDeviceForm();
      form.reset(device);

      final DialogWrapper dialog = form.createDialog(myProject);
      dialog.show();

      if (dialog.getExitCode() == OK_EXIT_CODE) {
        final LayoutDevice newDevice = myLayoutDeviceManager.replaceUserDevice(device, form.getName(), form.getXdpi(), form.getYdpi());
        selectedNode.setUserObject(newDevice);

        for (int i = 0; i < selectedNode.getChildCount(); i++) {
          final DefaultMutableTreeNode child = (DefaultMutableTreeNode)selectedNode.getChildAt(i);
          final LayoutDeviceConfiguration config = (LayoutDeviceConfiguration)child.getUserObject();
          config.setDevice(newDevice);
        }
        
        if (device.getName().equals(mySelectedDeviceName)) {
          mySelectedDeviceName = newDevice.getName();
        }

        nodeChanged(selectedNode);
      }
    }
    else {
      final LayoutDeviceConfiguration configuration = (LayoutDeviceConfiguration)o;
      final EditConfigurationDialog dialog = new EditConfigurationDialog(myProject, configuration);
      dialog.setTitle(AndroidBundle.message("android.layout.preview.edit.configuration.dialog.title"));
      dialog.show();

      if (dialog.getExitCode() == OK_EXIT_CODE) {
        final EditDeviceForm form = dialog.getEditDeviceForm();
        final LayoutDevice newDevice =
          myLayoutDeviceManager.replaceUserDevice(configuration.getDevice(), form.getName(), form.getXdpi(), form.getYdpi());
        final LayoutDeviceConfiguration newConfig = myLayoutDeviceManager
          .replaceUserConfiguration(newDevice, configuration.getName(), dialog.getConfigName(), dialog.getConfiguration());
        if (newConfig != null) {
          selectedNode.setUserObject(newConfig);
          final TreeNode deviceNode = selectedNode.getParent();
          ((DefaultMutableTreeNode)deviceNode).setUserObject(newDevice);

          for (int i = 0; i < deviceNode.getChildCount(); i++) {
            final DefaultMutableTreeNode child = (DefaultMutableTreeNode)deviceNode.getChildAt(i);
            final LayoutDeviceConfiguration config = (LayoutDeviceConfiguration)child.getUserObject();
            config.setDevice(newDevice);
          }

          nodeChanged(selectedNode);
          nodeStructureChanged(deviceNode);
          
          if (configuration.getName().equals(mySelectedDeviceConfigName)) {
            mySelectedDeviceConfigName = newConfig.getName();
          }
          
          if (configuration.getDevice().getName().equals(mySelectedDeviceName)) {
            mySelectedDeviceName = newDevice.getName();
          }
        }
      }
    }
  }

  @Nullable
  public String getSelectedDeviceName() {
    return mySelectedDeviceName;
  }

  @Nullable
  public String getSelectedDeviceConfigName() {
    return mySelectedDeviceConfigName;
  }

  private void doAdd() {
    final Object o = getSelectedDeviceOrDeviceConfig();

    LayoutDevice selectedDevice = null;

    if (o instanceof LayoutDevice) {
      selectedDevice = (LayoutDevice)o;
    }
    else if (o instanceof LayoutDeviceConfiguration) {
      selectedDevice = ((LayoutDeviceConfiguration)o).getDevice();
    }

    final EditConfigurationDialog dialog = new EditConfigurationDialog(myProject, selectedDevice != null &&
                                                                                  selectedDevice.getType() == LayoutDevice.Type.CUSTOM
                                                                                  ? selectedDevice
                                                                                  : null);
    dialog.setTitle(AndroidBundle.message("android.layout.preview.add.configuration.dialog.title"));
    dialog.show();

    if (dialog.getExitCode() != OK_EXIT_CODE) {
      return;
    }

    final EditDeviceForm editDeviceForm = dialog.getEditDeviceForm();
    final String deviceName = editDeviceForm.getName();
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode)myTable.getTree().getModel().getRoot();

    LayoutDevice device = myLayoutDeviceManager.getUserLayoutDevice(deviceName);
    DefaultMutableTreeNode deviceNode;

    if (device == null) {
      device = myLayoutDeviceManager.addUserDevice(deviceName, editDeviceForm.getXdpi(), editDeviceForm.getYdpi());
      deviceNode = new DefaultMutableTreeNode(device);
      if (myCustomCategoryRoot == null) {
        myCustomCategoryRoot = new DefaultMutableTreeNode(CUSTOM_CATEGORY_NAME);
        root.add(myCustomCategoryRoot);
        myCustomCategoryRoot.add(deviceNode);
        nodeStructureChanged(root);
      }
      else {
        myCustomCategoryRoot.add(deviceNode);
        nodeStructureChanged(myCustomCategoryRoot);
      }
    }
    else {
      final Pair<Integer, DefaultMutableTreeNode> pair =
        findObjectInTree((TreeNode)myTable.getTree().getModel().getRoot(), device);
      deviceNode = pair != null ? pair.second : null;
    }
    final LayoutDeviceConfiguration newConfig =
      myLayoutDeviceManager.addUserConfiguration(device, dialog.getConfigName(), dialog.getConfiguration());

    if (deviceNode != null && newConfig != null) {
      final DefaultMutableTreeNode newConfigNode = new DefaultMutableTreeNode(newConfig);
      deviceNode.add(newConfigNode);
      nodeStructureChanged(deviceNode);
      myTable.getTree().expandPath(new TreePath(deviceNode.getPath()));

      final Pair<Integer, DefaultMutableTreeNode> pair = findObjectInTree(root, newConfig);
      if (pair != null) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            selectRowInTableAndScroll(pair.first);
          }
        });
      }
    }
  }

  private void selectRowInTableAndScroll(int row) {
    selectRowInTable(row);
    TableScrollingUtil.ensureIndexIsVisible(myTable, row, 0);
  }

  private void nodeStructureChanged(TreeNode node) {
    final DefaultMutableTreeNode selectedNode = getSelectedNode();
    final Object selectedObj = selectedNode != null ? selectedNode.getUserObject() : null;

    final TreeTableTree tree = myTable.getTree();
    final DefaultTreeModel model = (DefaultTreeModel)tree.getModel();

    final Set<DefaultMutableTreeNode> expandedNodes = new HashSet<DefaultMutableTreeNode>();

    final TreeNode root = (TreeNode)tree.getModel().getRoot();
    for (int i = 0; i < root.getChildCount(); i++) {
      final TreeNode categoryNode = root.getChildAt(i);
      for (int j = 0; j < categoryNode.getChildCount(); j++) {
        final DefaultMutableTreeNode deviceNode = (DefaultMutableTreeNode)categoryNode.getChildAt(j);
        if (tree.isExpanded(new TreePath(deviceNode.getPath()))) {
          expandedNodes.add(deviceNode);
        }
      }
    }

    model.nodeChanged(node);
    model.nodeStructureChanged(node);

    for (DefaultMutableTreeNode expandedNode : expandedNodes) {
      tree.expandPath(new TreePath(expandedNode.getPath()));
    }

    if (selectedObj != null) {
      final Pair<Integer, DefaultMutableTreeNode> pair = findObjectInTree(root, selectedObj);
      if (pair != null) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            selectRowInTable(pair.first);
          }
        });
      }
    }
  }

  private void nodeChanged(TreeNode node) {
    final DefaultTreeModel model = (DefaultTreeModel)myTable.getTree().getModel();
    model.nodeChanged(node);
  }

  private void updateButtons() {
    final boolean enabled = isCustomDeviceConfigSelected();
    myEditButton.setEnabled(enabled);
    myRemoveButton.setEnabled(enabled);
  }

  private boolean isCustomDeviceConfigSelected() {
    final Object o = getSelectedDeviceOrDeviceConfig();
    if (o instanceof LayoutDevice) {
      return ((LayoutDevice)o).getType() == LayoutDevice.Type.CUSTOM;
    }
    else if (o instanceof LayoutDeviceConfiguration) {
      return ((LayoutDeviceConfiguration)o).getDevice().getType() == LayoutDevice.Type.CUSTOM;
    }
    return false;
  }

  @Nullable
  public Object getSelectedDeviceOrDeviceConfig() {
    final DefaultMutableTreeNode selectedNode = getSelectedNode();
    if (selectedNode != null) {
      final Object userObj = selectedNode.getUserObject();
      if (userObj instanceof LayoutDeviceConfiguration || userObj instanceof LayoutDevice) {
        return userObj;
      }
    }
    return null;
  }

  @Nullable
  public DefaultMutableTreeNode getSelectedNode() {
    final int selectedRow = myTable.getSelectedRow();
    if (selectedRow < 0) {
      return null;
    }

    final TreePath path = myTable.getTree().getPathForRow(selectedRow);
    if (path != null) {
      final Object component = path.getLastPathComponent();
      if (component instanceof DefaultMutableTreeNode) {
        return (DefaultMutableTreeNode)component;
      }
    }
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTable.getTree();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }
}
