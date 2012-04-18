package org.jetbrains.plugins.gradle.sync.conflict;

import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SeparatorFactory;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitor;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitorAdapter;
import org.jetbrains.plugins.gradle.diff.library.GradleMismatchedLibraryPathChange;
import org.jetbrains.plugins.gradle.model.id.GradleSyntheticId;
import org.jetbrains.plugins.gradle.ui.GradleIcons;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Collection;
import java.util.Set;

/**
 * Provides UI control for representing library setup conflicts.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 3/2/12 4:12 PM
 */
public class GradleLibraryDependencyConflictControlFactory {
  
  @NotNull private final GradleCommonDependencyConflictControlFactory myCommonPropertiesFactory;

  public GradleLibraryDependencyConflictControlFactory(@NotNull GradleCommonDependencyConflictControlFactory factory) {
    myCommonPropertiesFactory = factory;
  }

  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  public JComponent getControl(@NotNull LibraryOrderEntry libraryDependency, Collection<GradleProjectStructureChange> changes) {
    final Ref<GradleMismatchedLibraryPathChange> pathChangeRef = new Ref<GradleMismatchedLibraryPathChange>();
    GradleProjectStructureChangeVisitor visitor = new GradleProjectStructureChangeVisitorAdapter() {
      @Override
      public void visit(@NotNull GradleMismatchedLibraryPathChange change) {
        pathChangeRef.set(change);
      }
    };
    for (GradleProjectStructureChange change : changes) {
      if (!pathChangeRef.isNull()) {
        break;
      }
      change.invite(visitor);
    }

    JComponent commonChangesControl = myCommonPropertiesFactory.getControl(libraryDependency, changes);
    final GradleMismatchedLibraryPathChange pathChange = pathChangeRef.get();
    if (commonChangesControl == null && pathChange == null) {
      return null;
    }

    if (pathChange == null) {
      return commonChangesControl;
    }

    final Library library = libraryDependency.getLibrary();
    if (library == null) {
      return commonChangesControl;
    }
    
    final JComponent pathConflictControl = getPathConflictControl(library, pathChange);
    if (commonChangesControl == null) {
      return pathConflictControl;
    }
    
    JPanel result = new JPanel(new GridBagLayout());
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.weightx = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    final JComponent commonSeparator =
      SeparatorFactory.createSeparator(GradleBundle.message("gradle.sync.change.dependency.common.title"), commonChangesControl);
    commonSeparator.setBackground(pathConflictControl.getBackground());
    result.add(commonSeparator, constraints);
    result.add(commonChangesControl, constraints);
    final JComponent pathSeparator =
      SeparatorFactory.createSeparator(GradleBundle.message("gradle.sync.change.library.path.title"), pathConflictControl);
    pathSeparator.setBackground(pathConflictControl.getBackground());
    result.add(pathSeparator, constraints);
    result.add(pathConflictControl, constraints);

    return result;
  }

  private static JComponent getPathConflictControl(@NotNull Library library, @NotNull GradleMismatchedLibraryPathChange pathChange) {
    GradleProjectStructureNode<GradleSyntheticId> root = new GradleProjectStructureNode<GradleSyntheticId>(
      GradleUtil.buildSyntheticDescriptor(GradleUtil.getLibraryName(library), GradleIcons.LIB_ICON)
    );
    for (String path : pathChange.getGradleValue()) {
      final GradleProjectStructureNode<GradleSyntheticId> node = buildPathNode(path);
      node.setAttributes(GradleTextAttributes.GRADLE_LOCAL_CHANGE);
      root.add(node);
    }

    final Set<String> intellijLocalBinaries = pathChange.getIntellijValue();
    for (VirtualFile libRoot : library.getFiles(OrderRootType.CLASSES)) {
      final String path = GradleUtil.getLocalFileSystemPath(libRoot);
      GradleProjectStructureNode<GradleSyntheticId> node = buildPathNode(path);
      if (intellijLocalBinaries.contains(path)) {
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
