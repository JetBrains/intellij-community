package org.jetbrains.plugins.gradle.config;

import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.application.options.colors.ColorAndFontSettingsListener;
import com.intellij.application.options.colors.EditorSchemeAttributeDescriptor;
import com.intellij.application.options.colors.PreviewPanel;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.id.GradleSyntheticId;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeDescriptor;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 1/19/12 11:59 AM
 */
public class GradleColorAndFontPreviewPanel implements PreviewPanel {
  
  private final Map<TextAttributesKey, DefaultMutableTreeNode> myNodes = new HashMap<TextAttributesKey, DefaultMutableTreeNode>();

  private final List<ColorAndFontSettingsListener> myListeners                = ContainerUtil.createEmptyCOWList();
  private final JPanel                             myContent                  = new JPanel(new GridBagLayout());
  private final JPanel                             myNodeRenderPanel          = new JPanel(new GridBagLayout());
  private final Ref<Boolean>                       myAllowTreeExpansion       = new Ref<Boolean>(true);
  private final ArrowPanel                         mySelectedElementSignPanel = new ArrowPanel();
  
  private final Tree             myTree;
  private final DefaultTreeModel myTreeModel;

  private ColorAndFontOptions myOptions;
  private TreeNode            mySelectedNode;

  public GradleColorAndFontPreviewPanel(@NotNull ColorAndFontOptions options) {
    myOptions = options;
    final Pair<Tree, DefaultTreeModel> pair = init();
    myTree = pair.first;
    myTreeModel = pair.second;
  }

  private Pair<Tree, DefaultTreeModel> init() {
    myContent.removeAll();
    String projectName = GradleBundle.message("gradle.settings.color.text.sample.conflict.node.name");
    DefaultMutableTreeNode root = createNode(
      projectName,
      IconLoader.getIcon(ApplicationInfoEx.getInstanceEx().getSmallIconUrl()),
      GradleTextAttributes.CHANGE_CONFLICT
    );

    String moduleName = GradleBundle.message("gradle.settings.color.text.sample.node.sync.name");
    DefaultMutableTreeNode module = createNode(moduleName, AllIcons.Nodes.Module, GradleTextAttributes.NO_CHANGE);

    String gradleLibraryName = GradleBundle.message("gradle.settings.color.text.sample.node.gradle.name");
    DefaultMutableTreeNode gradleLibrary = createNode(
      gradleLibraryName, AllIcons.Nodes.PpLib, GradleTextAttributes.GRADLE_LOCAL_CHANGE
    );

    String intellijLibraryName = GradleBundle.message("gradle.settings.color.text.sample.node.intellij.name");
                                                      //ApplicationNamesInfo.getInstance().getProductName());
    DefaultMutableTreeNode intellijLibrary = createNode(
      intellijLibraryName, AllIcons.Nodes.PpLib, GradleTextAttributes.INTELLIJ_LOCAL_CHANGE
    );
    
    //String syncLibraryName = GradleBundle.message("gradle.settings.color.text.sample.node.sync.name");
    //DefaultMutableTreeNode syncLibrary = createNode(syncLibraryName, GradleIcons.LIB_ICON, GradleTextAttributes.NO_CHANGE);
    
    module.add(gradleLibrary);
    module.add(intellijLibrary);
    //module.add(syncLibrary);
    root.add(module);
    
    mySelectedNode = root;
    
    DefaultTreeModel treeModel = new DefaultTreeModel(root);
    Tree tree = buildTree(treeModel, module);
    
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = constraints.weighty = 1;
    myContent.add(new JBScrollPane(tree), constraints);
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
            pointTo(entry.getValue());
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
        if (myNodeRenderPanel.getComponentCount() <= 0) {
          GridBagConstraints constraints = new GridBagConstraints();
          myNodeRenderPanel.add(component, constraints);
          constraints.weightx = 1;
          constraints.anchor = GridBagConstraints.CENTER;
          myNodeRenderPanel.add(mySelectedElementSignPanel, constraints);
        }
        
        mySelectedElementSignPanel.setPaint(value == mySelectedNode);
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
        pointTo(entry.getValue());
        return;
      }
    }
  }

  /**
   * Instructs the panel to show given node as selected.
   * 
   * @param node  node to show as 'selected'
   */
  private void pointTo(@NotNull TreeNode node) {
    TreeNode oldSelectedNode = mySelectedNode;
    mySelectedNode = node;
    myTreeModel.nodeChanged(oldSelectedNode);
    myTreeModel.nodeChanged(mySelectedNode);
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
    final GradleProjectStructureNodeDescriptor<GradleSyntheticId> descriptor
      = new GradleProjectStructureNodeDescriptor<GradleSyntheticId>(new GradleSyntheticId(text), text, icon);
    DefaultMutableTreeNode result = new DefaultMutableTreeNode(descriptor);
    if (textAttributesKey != null) {
      final PresentationData presentation = descriptor.getPresentation();
      presentation.setAttributesKey(textAttributesKey);
      myNodes.put(textAttributesKey, result);
    }
    return result;
  }

  /**
   * Encapsulates logic of drawing 'selected element' sign.
   */
  private static class ArrowPanel extends JPanel {
    
    private boolean myPaint;
    
    ArrowPanel() {
      super(new BorderLayout());
      // Reserve enough horizontal space.
      add(new JLabel("intelli"));
    }

    public void setPaint(boolean paint) {
      myPaint = paint;
    }

    @Override
    public void paint(Graphics g) {
      if (!myPaint) {
        return;
      }
      Graphics2D g2 = (Graphics2D)g;
      g.setColor(JBColor.RED);
      RenderingHints renderHints = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      renderHints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g2.setRenderingHints(renderHints);

      FontMetrics fontMetrics = getFontMetrics(getFont());
      int unit = fontMetrics.charWidth('a') * 2 / 3;
      int yShift = 0;
      final Dimension size = getSize();
      if (size != null) {
        yShift = size.height / 2 - unit;
        if (size.height % 2 != 0) {
          // Prefer 'draw below the center' to 'draw above the center'.
          yShift++;
        }
      }
      int q = unit / 4;
      int[] x = {0,    unit * 3, unit * 2, unit * 4, unit * 4, unit * 2, unit * 3, 0   };
      int[] y = {unit, 0,        unit - q, unit - q, unit + q, unit + q, unit * 2, unit};
      if (yShift != 0) {
        for (int i = 0; i < y.length; i++) {
          y[i] += yShift;
        }
      }
      g2.fillPolygon(x, y, x.length);
    }
  }
}
