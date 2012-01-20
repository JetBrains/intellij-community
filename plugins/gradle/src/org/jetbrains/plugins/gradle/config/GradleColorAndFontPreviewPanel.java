package org.jetbrains.plugins.gradle.config;

import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.application.options.colors.ColorAndFontSettingsListener;
import com.intellij.application.options.colors.EditorSchemeAttributeDescriptor;
import com.intellij.application.options.colors.PreviewPanel;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.hash.HashMap;
import org.jdesktop.swingx.JXPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.ui.GradleIcons;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeDescriptor;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Denis Zhdanov
 * @since 1/19/12 11:59 AM
 */
public class GradleColorAndFontPreviewPanel implements PreviewPanel {
  
  private static final int   SELECTED_NODE_REFRESH_INTERVAL_MILLIS = 100;
  private static final float BLINK_ALPHA_INCREMENT                 = 0.1f;

  private final Map<TextAttributesKey, DefaultMutableTreeNode> myNodes = new HashMap<TextAttributesKey, DefaultMutableTreeNode>();
  
  private final List<ColorAndFontSettingsListener> myListeners          = new CopyOnWriteArrayList<ColorAndFontSettingsListener>();
  private final JPanel                             myContent            = new JPanel(new GridBagLayout());
  private final JXPanel                            myNodeRenderPanel    = new JXPanel(new BorderLayout());
  private final Ref<Boolean>                       myAllowTreeExpansion = new Ref<Boolean>(true);
  
  private final Tree             myTree;
  private final DefaultTreeModel myTreeModel;

  private Timer               myTimer;
  private ColorAndFontOptions myOptions;
  private TreeNode            mySelectedNode;
  private float               mySelectedNodeAlpha;
  private boolean             mySelectedNodePainted;

  public GradleColorAndFontPreviewPanel(@NotNull ColorAndFontOptions options) {
    myOptions = options;
    final Pair<Tree, DefaultTreeModel> pair = init();
    myTree = pair.first;
    myTreeModel = pair.second;
  }

  private Pair<Tree, DefaultTreeModel> init() {
    myContent.removeAll();
    String projectName = GradleBundle.message("gradle.settings.color.text.sample.project.name");
    DefaultMutableTreeNode root = createNode(projectName, GradleIcons.PROJECT_ICON, null);

    String moduleName = GradleBundle.message("gradle.settings.color.text.sample.conflict.module.name");
    DefaultMutableTreeNode module = createNode(moduleName, GradleIcons.MODULE_ICON, GradleTextAttributes.GRADLE_CHANGE_CONFLICT);

    String gradleLibraryName = GradleBundle.message("gradle.settings.color.text.sample.library.gradle.name");
    DefaultMutableTreeNode gradleLibrary = createNode(gradleLibraryName, GradleIcons.LIB_ICON, GradleTextAttributes.GRADLE_LOCAL_CHANGE);

    String intellijLibraryName = GradleBundle.message("gradle.settings.color.text.sample.library.intellij.name",
                                                      ApplicationNamesInfo.getInstance().getProductName());
    DefaultMutableTreeNode intellijLibrary = createNode(
      intellijLibraryName, GradleIcons.LIB_ICON, GradleTextAttributes.GRADLE_INTELLIJ_LOCAL_CHANGE
    );
    
    String confirmedLibraryName = GradleBundle.message("gradle.settings.color.text.sample.library.confirmed.name");
    DefaultMutableTreeNode confirmedLibrary = createNode(
      confirmedLibraryName, GradleIcons.LIB_ICON, GradleTextAttributes.GRADLE_CONFIRMED_CONFLICT
    );

    String syncLibraryName = GradleBundle.message("gradle.settings.color.text.sample.library.sync.name");
    DefaultMutableTreeNode syncLibrary = createNode(syncLibraryName, GradleIcons.LIB_ICON, GradleTextAttributes.GRADLE_NO_CHANGE);
    
    module.add(gradleLibrary);
    module.add(intellijLibrary);
    module.add(confirmedLibrary);
    module.add(syncLibrary);
    root.add(module);
    
    DefaultTreeModel treeModel = new DefaultTreeModel(root);
    Tree tree = buildTree(treeModel, module);
    
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = constraints.weighty = 1;
    myContent.add(tree, constraints);
    return new Pair<Tree, DefaultTreeModel>(tree, treeModel);
  }
  
  private Tree buildTree(@NotNull TreeModel model, DefaultMutableTreeNode ... nodesToExpand) {
    final Tree tree = new Tree(model) {
      @Override
      protected void setExpandedState(TreePath path, boolean state) {
        if (myAllowTreeExpansion.get()) {
          super.setExpandedState(path, state);
        }
        // Ignore the expansion change events otherwise
      }
    };
    
    // Configure expansion
    for (DefaultMutableTreeNode node : nodesToExpand) {
      tree.expandPath(new TreePath(node.getPath()));
    }
    myAllowTreeExpansion.set(false);
    
    // Configure selection.
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.addTreeSelectionListener(new TreeSelectionListener() {
      
      private boolean myIgnore;
      
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        if (myIgnore) {
          return;
        }
        final TreePath path = e.getNewLeadSelectionPath();
        if (path == null) {
          clearSelection();
          return;
        }
        final Object component = path.getLastPathComponent();
        for (Map.Entry<TextAttributesKey, DefaultMutableTreeNode> entry : myNodes.entrySet()) {
          if (entry.getValue().equals(component)) {
            for (ColorAndFontSettingsListener listener : myListeners) {
              listener.selectionInPreviewChanged(entry.getKey().getExternalName());
              clearSelection();
            }
            return;
          }
        }
        clearSelection();
      }

      private void clearSelection() {
        // Don't show selection at the 'preview' node.
        myIgnore = true;
        try {
          tree.getSelectionModel().clearSelection();
        }
        finally {
          myIgnore = false;
        }
      }
    });
    
    // Bind rendering to the target colors scheme.
    final NodeRenderer delegate = new NodeRenderer() {
      @NotNull
      @Override
      protected EditorColorsScheme getColorsScheme() {
        return myOptions.getSelectedScheme();
      }
    };
    myNodeRenderPanel.setBackground(tree.getBackground());
    myNodeRenderPanel.setPaintBorderInsets(false);
    tree.setCellRenderer(new TreeCellRenderer() {
      @Override
      public Component getTreeCellRendererComponent(JTree tree,
                                                    Object value,
                                                    boolean selected,
                                                    boolean expanded,
                                                    boolean leaf,
                                                    int row,
                                                    boolean hasFocus)
      {
        final Component component = delegate.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        myNodeRenderPanel.removeAll();
        myNodeRenderPanel.add(component);
        if (value == mySelectedNode) {
          myNodeRenderPanel.setAlpha(roundAlpha(mySelectedNodeAlpha));
          mySelectedNodePainted = true;
        }
        else {
          myNodeRenderPanel.setAlpha(roundAlpha(1.1f - mySelectedNodeAlpha));
        }
        return myNodeRenderPanel;
      }
    });
    return tree;
  }
  
  @Override
  public void blinkSelectedHighlightType(Object selected) {
    if (!(selected instanceof EditorSchemeAttributeDescriptor)) {
      return;
    }
    final String type = ((EditorSchemeAttributeDescriptor)selected).getType();
    for (Map.Entry<TextAttributesKey, DefaultMutableTreeNode> entry : myNodes.entrySet()) {
      if (entry.getKey().getExternalName().equals(type)) {
        blink(entry.getValue());
        return;
      }
    }
  }

  private void blink(@NotNull TreeNode node) {
    if (myTimer != null) {
      myTimer.stop();
    }
    mySelectedNode = node;
    mySelectedNodeAlpha = 0.5f;
    myTimer = new Timer(SELECTED_NODE_REFRESH_INTERVAL_MILLIS, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (mySelectedNodeAlpha > 1) {
          mySelectedNodeAlpha = 1;
          myTimer.stop();
          repaintTree();
          return;
        }
        if (!mySelectedNodePainted) {
          return;
        }
        mySelectedNodePainted = false;
        mySelectedNodeAlpha += BLINK_ALPHA_INCREMENT;
        repaintTree();
      }
    });
    myTimer.start();
  }
  
  @Override
  public void disposeUIResources() {
    myListeners.clear();
    myNodes.clear();
    myContent.removeAll();
    myNodeRenderPanel.removeAll();
  }

  @Override
  public Component getPanel() {
    return myContent;
  }

  @Override
  public void updateView() {
    repaintTree();
  }

  private void repaintTree() {
    // TODO den implement
    myTreeModel.reload();
    myAllowTreeExpansion.set(true);
    try {
      for (DefaultMutableTreeNode node : myNodes.values()) {
        myTree.expandPath(new TreePath(node.getPath()));
      }
    }
    finally {
      myAllowTreeExpansion.set(false);
    }
  }

  @Override
  public void addListener(@NotNull ColorAndFontSettingsListener listener) {
    myListeners.add(listener);
  }

  private DefaultMutableTreeNode createNode(@NotNull String text, @NotNull Icon icon, @Nullable TextAttributesKey textAttributesKey) {
    final GradleProjectStructureNodeDescriptor<String> descriptor = new GradleProjectStructureNodeDescriptor<String>(text, text, icon);
    DefaultMutableTreeNode result = new DefaultMutableTreeNode(descriptor);
    if (textAttributesKey != null) {
      final PresentationData presentation = descriptor.getPresentation();
      presentation.setAttributesKey(textAttributesKey);
      myNodes.put(textAttributesKey, result);
    }
    return result;
  }
  
  private static float roundAlpha(float alpha) {
    if (alpha < 0) {
      return 0;
    }
    else if (alpha > 1) {
      return 1;
    }
    else {
      return alpha;
    } 
  }
}
