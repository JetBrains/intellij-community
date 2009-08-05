package com.intellij.lang.ant.config.explorer;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.impl.AntBeforeRunTask;
import com.intellij.lang.ant.config.impl.AntBeforeRunTaskProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.StringSetSpinAllocator;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Set;

public final class ExecuteOnRunDialog extends DialogWrapper {
  private final Project myProject;
  private final AntBuildTarget myTarget;
  private final AntBuildFile myFile;
  private DefaultMutableTreeNode myRoot;

  public ExecuteOnRunDialog(final Project project, final AntBuildTarget target, final AntBuildFile file) {
    super(project, true);
    myProject = project;
    myTarget = target;
    myFile = file;
    setTitle(AntBundle.message("execute.target.before.run.debug.dialog.title"));
    init();
  }


  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    myRoot = buildNodes();
    final Tree tree = new Tree(myRoot);

    final MyTreeCellRenderer cellRenderer = new MyTreeCellRenderer();

    tree.setCellRenderer(cellRenderer);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setLineStyleAngled();
    TreeToolTipHandler.install(tree);
    TreeUtil.installActions(tree);
    new TreeSpeedSearch(tree);

    tree.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        int row = tree.getRowForLocation(e.getX(), e.getY());
        if (row >= 0) {
          Rectangle rowBounds = tree.getRowBounds(row);
          cellRenderer.setBounds(rowBounds);
          Rectangle checkBounds = cellRenderer.myCheckbox.getBounds();

          checkBounds.setLocation(rowBounds.getLocation());
          if (checkBounds.contains(e.getPoint())) {
            toggleNode(tree, (DefaultMutableTreeNode)tree.getPathForRow(row).getLastPathComponent());
            e.consume();
            tree.setSelectionRow(row);
          }
        }
      }
    });

    tree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
          TreePath treePath = tree.getLeadSelectionPath();
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
          toggleNode(tree, node);
          e.consume();
        }
      }
    });

    expacndChecked(tree);

    JScrollPane scrollPane = new JScrollPane(tree);
    scrollPane.setPreferredSize(new Dimension(400, 400));
    panel.add(scrollPane, BorderLayout.CENTER);
    return panel;
  }

  private static void expacndChecked(Tree tree) {
    TreeNode root = (TreeNode)tree.getModel().getRoot();
    Enumeration factories = root.children();
    ArrayList<TreeNode[]> toExpand = new ArrayList<TreeNode[]>();
    while (factories.hasMoreElements()) {
      DefaultMutableTreeNode factoryNode = (DefaultMutableTreeNode)factories.nextElement();
      Enumeration configurations = factoryNode.children();
      while (configurations.hasMoreElements()) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)configurations.nextElement();
        ConfigurationDescriptor config = (ConfigurationDescriptor)node.getUserObject();
        if (config.isChecked()) {
          toExpand.add(factoryNode.getPath());
          break;
        }
      }
    }
    for (TreeNode[] treeNodes : toExpand) {
      tree.expandPath(new TreePath(treeNodes));
    }
  }

  private static void toggleNode(JTree tree, DefaultMutableTreeNode node) {
    Descriptor descriptor = (Descriptor)node.getUserObject();
    descriptor.setChecked(!descriptor.isChecked());
    tree.repaint();
  }

  private DefaultMutableTreeNode buildNodes() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode(new Descriptor());
    RunManager runManager = RunManager.getInstance(myProject);
    final ConfigurationType[] configTypes = runManager.getConfigurationFactories();

    for (final ConfigurationType type : configTypes) {
      final Icon icon = type.getIcon();
      DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(new ConfigurationTypeDescriptor(type, icon, isConfigurationAssigned(type)));
      root.add(typeNode);
      final Set<String> addedNames = StringSetSpinAllocator.alloc();
      try {
        RunConfiguration[] configurations = runManager.getConfigurations(type);
        for (final RunConfiguration configuration : configurations) {
          final String configurationName = configuration.getName();
          if (addedNames.contains(configurationName)) {
            // add only the first configuration if more than one has the same name
            continue;
          }
          addedNames.add(configurationName);
          typeNode.add(new DefaultMutableTreeNode(
            new ConfigurationDescriptor(configuration, isConfigurationAssigned(configuration))));
        }
      }
      finally {
        StringSetSpinAllocator.dispose(addedNames);
      }
    }

    return root;
  }


  private boolean isConfigurationAssigned(ConfigurationType type) {
    final RunManagerEx runManager = RunManagerEx.getInstanceEx(myProject);
    for (ConfigurationFactory factory : type.getConfigurationFactories()) {
      final RunnerAndConfigurationSettingsImpl settings = ((RunManagerImpl)runManager).getConfigurationTemplate(factory);
      final AntBeforeRunTask task = runManager.getBeforeRunTask(settings.getConfiguration(), AntBeforeRunTaskProvider.ID);
      if (task != null && task.isRunningTarget(myTarget)) {
        return true;
      }
    }
    return false;
  }
  private boolean isConfigurationAssigned(RunConfiguration configuration) {
    final AntBeforeRunTask task = RunManagerEx.getInstanceEx(myProject).getBeforeRunTask(configuration, AntBeforeRunTaskProvider.ID);
    return task != null && task.isRunningTarget(myTarget);
  }

  protected void doOKAction() {
    final RunManagerImpl runManager = (RunManagerImpl)RunManagerEx.getInstanceEx(myProject);
    for (Enumeration nodes = myRoot.depthFirstEnumeration(); nodes.hasMoreElements(); ) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)nodes.nextElement();
      final Descriptor descriptor = (Descriptor)node.getUserObject();
      final boolean isChecked = descriptor.isChecked();
      final String targetName;
      final String antfileUrl;
      if (isChecked) {
        final VirtualFile vFile = myTarget.getModel().getBuildFile().getVirtualFile();
        targetName = vFile != null? myTarget.getName() : null;
        antfileUrl = vFile != null? vFile.getUrl() : null;
      }
      else {
        targetName = null;
        antfileUrl = null;
      }
      if (descriptor instanceof ConfigurationTypeDescriptor) {
        final ConfigurationTypeDescriptor configurationTypeDescriptor = (ConfigurationTypeDescriptor)descriptor;
        for (ConfigurationFactory factory : configurationTypeDescriptor.getConfigurationType().getConfigurationFactories()) {
          final RunnerAndConfigurationSettingsImpl settings = runManager.getConfigurationTemplate(factory);
          final AntBeforeRunTask task = runManager.getBeforeRunTask(settings.getConfiguration(), AntBeforeRunTaskProvider.ID);
          if (task != null && (isChecked || task.isRunningTarget(myTarget))) {
            task.setEnabled(isChecked);
            task.setAntFileUrl(antfileUrl);
            task.setTargetName(targetName);
          }
        }
      }
      else if (descriptor instanceof ConfigurationDescriptor) {
        final ConfigurationDescriptor configurationDescriptor = (ConfigurationDescriptor)descriptor;
        final AntBeforeRunTask task = runManager.getBeforeRunTask(configurationDescriptor.getConfiguration(), AntBeforeRunTaskProvider.ID);
        if (task != null && (isChecked || task.isRunningTarget(myTarget))) {
          task.setEnabled(isChecked);
          task.setAntFileUrl(antfileUrl);
          task.setTargetName(targetName);
        }
      }
    }

    close(OK_EXIT_CODE);
  }

  private static class Descriptor {
    private boolean myChecked;

    public final boolean isChecked() {
      return myChecked;
    }

    public final void setChecked(boolean checked) {
      myChecked = checked;
    }
  }

  private static final class ConfigurationTypeDescriptor extends Descriptor {
    private final ConfigurationType myConfigurationType;
    private final Icon myIcon;

    public ConfigurationTypeDescriptor(ConfigurationType type, Icon icon, boolean isChecked) {
      myConfigurationType = type;
      myIcon = icon;
      setChecked(isChecked);
    }

    public ConfigurationType getConfigurationType() {
      return myConfigurationType;
    }

    public Icon getIcon() {
      return myIcon;
    }
  }

  private static final class ConfigurationDescriptor extends Descriptor {
    private final RunConfiguration myConfiguration;

    public ConfigurationDescriptor(RunConfiguration configuration, boolean isChecked) {
      myConfiguration = configuration;
      setChecked(isChecked);
    }

    public ConfigurationType getConfigurationFactory() {
      return myConfiguration.getType();
    }

    public String getName() {
      return myConfiguration.getName();
    }

    public RunConfiguration getConfiguration() {
      return myConfiguration;
    }
  }


  private static final class MyTreeCellRenderer extends JPanel implements TreeCellRenderer {
    private final JLabel myLabel;
    public final JCheckBox myCheckbox;

    public MyTreeCellRenderer() {
      super(new BorderLayout());
      myCheckbox = new JCheckBox();
      myLabel = new JLabel();
      add(myCheckbox, BorderLayout.WEST);
      add(myLabel, BorderLayout.CENTER);
    }

    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      Descriptor descriptor = (Descriptor)node.getUserObject();

      myCheckbox.setSelected(descriptor.isChecked());

      myCheckbox.setBackground(UIUtil.getTreeTextBackground());
      setBackground(selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground());
      Color foreground = selected ? UIUtil.getTreeSelectonForeground() : UIUtil.getTreeTextForeground();
      setForeground(foreground);
      myCheckbox.setForeground(foreground);
      myLabel.setForeground(foreground);
      myCheckbox.setEnabled(true);

      if (descriptor instanceof ConfigurationTypeDescriptor) {
        ConfigurationTypeDescriptor configurationTypeDescriptor = (ConfigurationTypeDescriptor)descriptor;
        myLabel.setFont(tree.getFont());
        myLabel.setText(configurationTypeDescriptor.getConfigurationType().getDisplayName());
        myLabel.setIcon(configurationTypeDescriptor.getIcon());
      }
      else if (descriptor instanceof ConfigurationDescriptor) {
        ConfigurationDescriptor configurationTypeDescriptor = (ConfigurationDescriptor)descriptor;
        myLabel.setFont(tree.getFont());
        myLabel.setText(configurationTypeDescriptor.getName());
        myLabel.setIcon(null);

        if (((ConfigurationTypeDescriptor)((DefaultMutableTreeNode)node.getParent()).getUserObject()).isChecked()) {
          Color foregrnd = tree.getForeground();
          Color backgrnd = tree.getBackground();
          if (foregrnd == null) foregrnd = Color.black;
          if (backgrnd == null) backgrnd = Color.white;

          int red = (foregrnd.getRed() + backgrnd.getRed()) / 2;
          int green = (foregrnd.getGreen() + backgrnd.getGreen()) / 2;
          int blue = (foregrnd.getBlue() + backgrnd.getBlue()) / 2;
          Color halftone = new Color(red, green, blue);
          setForeground(halftone);
          myCheckbox.setForeground(halftone);
          myLabel.setForeground(halftone);
          myCheckbox.setEnabled(false);
        }
      }

      return this;
    }

  }
}
