package org.jetbrains.plugins.gradle.sync.conflict;

import com.intellij.openapi.roots.ExportableOrderEntry;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitor;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitorAdapter;
import org.jetbrains.plugins.gradle.diff.dependency.GradleDependencyExportedChange;
import org.jetbrains.plugins.gradle.diff.dependency.GradleDependencyScopeChange;
import org.jetbrains.plugins.gradle.model.id.GradleEntityId;
import org.jetbrains.plugins.gradle.model.id.GradleEntityIdMapper;
import org.jetbrains.plugins.gradle.ui.MatrixControlBuilder;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import java.util.Collection;

/**
 * Encapsulates functionality of building UI that represents common dependency settings conflicts (scope, 'exported').
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 3/14/12 2:20 PM
 */
public class GradleCommonDependencyConflictControlFactory {

  /**
   * Allows to build UI for showing common dependency settings conflicts (scope, 'exported'). 
   * 
   * @param dependency  target dependency which conflict changes should be shown
   * @param changes     target changes to use represent
   * @return            UI control for showing common dependency settings conflicts if they are present at the given settings;
   *                    <code>null</code> otherwise
   */
  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  public JComponent getControl(@NotNull ExportableOrderEntry dependency, @NotNull Collection<GradleProjectStructureChange> changes) {
    final GradleEntityId id = GradleEntityIdMapper.mapEntityToId(dependency);
    final Ref<GradleDependencyScopeChange> scopeChangeRef = new Ref<GradleDependencyScopeChange>();
    final Ref<GradleDependencyExportedChange> exportedChangeRef = new Ref<GradleDependencyExportedChange>();
    GradleProjectStructureChangeVisitor visitor = new GradleProjectStructureChangeVisitorAdapter() {
      @Override
      public void visit(@NotNull GradleDependencyScopeChange change) {
        if (id.equals(change.getEntityId())) {
          scopeChangeRef.set(change);
        }
      }

      @Override
      public void visit(@NotNull GradleDependencyExportedChange change) {
        if (id.equals(change.getEntityId())) {
          exportedChangeRef.set(change);
        }
      }
    };
    for (GradleProjectStructureChange change : changes) {
      if (scopeChangeRef.get() != null && exportedChangeRef.get() != null) {
        break;
      }
      change.invite(visitor);
    }

    final GradleDependencyScopeChange scopeChange = scopeChangeRef.get();
    final GradleDependencyExportedChange exportedChange = exportedChangeRef.get();
    if (scopeChange == null && exportedChange == null) {
      return null;
    }
    MatrixControlBuilder builder = GradleUtil.getConflictChangeBuilder();
    if (scopeChange != null) {
      builder.addRow(GradleBundle.message("gradle.sync.change.dependency.scope.text"),
                     scopeChange.getGradleValue(), scopeChange.getIntellijValue());
    }
    if (exportedChange != null) {
      builder.addRow(GradleBundle.message("gradle.sync.change.dependency.exported.text"),
                     exportedChange.getGradleValue(), exportedChange.getIntellijValue());
    }
    return builder.build();
  }
}
