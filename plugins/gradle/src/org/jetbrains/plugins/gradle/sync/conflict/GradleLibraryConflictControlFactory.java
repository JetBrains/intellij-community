package org.jetbrains.plugins.gradle.sync.conflict;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.diff.library.GradleMismatchedLibraryPathChange;
import org.jetbrains.plugins.gradle.model.id.GradleSyntheticId;
import org.jetbrains.plugins.gradle.ui.GradleIcons;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.Set;

/**
 * Provides UI control for representing library setup conflicts.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 3/2/12 4:12 PM
 */
public class GradleLibraryConflictControlFactory {

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  public JComponent getControl(@NotNull Library library, @NotNull GradleMismatchedLibraryPathChange change) {
    GradleProjectStructureNode<GradleSyntheticId> root = new GradleProjectStructureNode<GradleSyntheticId>(
      GradleUtil.buildSyntheticDescriptor(GradleUtil.getLibraryName(library), GradleIcons.LIB_ICON)
    );
    final Set<String> gradleLocalBinaryPaths = change.getGradleValue();
    if (gradleLocalBinaryPaths != null) {
      for (String path : gradleLocalBinaryPaths) {
        final GradleProjectStructureNode<GradleSyntheticId> node = buildPathNode(path);
        node.setAttributes(GradleTextAttributes.GRADLE_LOCAL_CHANGE);
        root.add(node);
      }
    }
    
    final Set<String> intellijLocalBinaries = change.getIntellijValue();
    for (VirtualFile libRoot : library.getFiles(OrderRootType.CLASSES)) {
      final String path = GradleUtil.getLocalFileSystemPath(libRoot);
      GradleProjectStructureNode<GradleSyntheticId> node = buildPathNode(path);
      if (intellijLocalBinaries != null && intellijLocalBinaries.contains(path)) {
        node.setAttributes(GradleTextAttributes.INTELLIJ_LOCAL_CHANGE);
      }
      root.add(node);
    }

    DefaultTreeModel model = new DefaultTreeModel(root);
    return new Tree(model);
  }

  private static GradleProjectStructureNode<GradleSyntheticId> buildPathNode(@NotNull String path) {
    final int i = path.lastIndexOf('/');
    final String name = i < 0 || i >= path.length() - 1 ? path : path.substring(i + 1);
    final GradleProjectStructureNode<GradleSyntheticId> result
      = new GradleProjectStructureNode<GradleSyntheticId>(GradleUtil.buildSyntheticDescriptor(name, GradleIcons.JAR_ICON));
    result.getDescriptor().setToolTip(path);
    return result;
  }
}
