package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleToolWindowPanel;
import org.jetbrains.plugins.gradle.ui.GradleIcons;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeDescriptor;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

/**
 * // TODO den add doc
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:58 PM
 */
public class GradleProjectStructureChangesPanel extends GradleToolWindowPanel {

  private JPanel myContent;
  
  private final GradleProjectStructureChangesModel myDataModel;

  public GradleProjectStructureChangesPanel(@NotNull Project project, @NotNull GradleProjectStructureChangesModel model) {
    super(project, GradleConstants.TOOL_WINDOW_TOOLBAR_PLACE);
    myDataModel = model;
    init();
  }

  private void init() {
    JPanel content = new JPanel(new GridBagLayout());
    DefaultMutableTreeNode root = new DefaultMutableTreeNode(buildDescriptor(getProject()));
    final Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    RootPolicy<LibraryOrderEntry> policy = new RootPolicy<LibraryOrderEntry>() {
      @Override
      public LibraryOrderEntry visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, LibraryOrderEntry value) {
        return libraryOrderEntry;
      }
    };
    for (Module module : modules) {
      final DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(buildDescriptor(module));
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      for (OrderEntry orderEntry : moduleRootManager.getOrderEntries()) {
        final LibraryOrderEntry library = orderEntry.accept(policy, null);
        if (library != null) {
          moduleNode.add(new DefaultMutableTreeNode(buildDescriptor(library)));
        }
      }
      root.add(moduleNode);
    }
    DefaultTreeModel treeModel = new DefaultTreeModel(root);
    Tree tree = new Tree(treeModel);

    GridBagConstraints constraints = new GridBagConstraints();
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = constraints.weighty = 1;
    content.add(tree, constraints);
    myContent = content;
  }
  
  @NotNull
  @Override
  protected JComponent buildContent() {
    if (myContent == null) {
      init();
    }
    return myContent;
  }

  @Override
  protected void updateContent() {
    // TODO den implement
    int i = 1;
  }

  public static GradleProjectStructureNodeDescriptor<Project> buildDescriptor(@NotNull Project project) {
    return new GradleProjectStructureNodeDescriptor<Project>(project, project.getName(), GradleIcons.PROJECT_ICON);
  }

  public static GradleProjectStructureNodeDescriptor<Module> buildDescriptor(@NotNull Module module) {
    return new GradleProjectStructureNodeDescriptor<Module>(module, module.getName(), GradleIcons.MODULE_ICON);
  }

  public static GradleProjectStructureNodeDescriptor<LibraryOrderEntry> buildDescriptor(@NotNull LibraryOrderEntry library) {
    return new GradleProjectStructureNodeDescriptor<LibraryOrderEntry>(library, library.getPresentableName(), GradleIcons.LIB_ICON);
  }
}
