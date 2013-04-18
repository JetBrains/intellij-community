package org.jetbrains.plugins.gradle.sync.conflict;

import com.intellij.openapi.externalSystem.model.project.change.*;
import com.intellij.openapi.externalSystem.model.project.id.EntityIdMapper;
import com.intellij.openapi.externalSystem.model.project.id.ProjectEntityId;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.roots.ExportableOrderEntry;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.externalSystem.model.project.change.DependencyExportedChange;
import org.jetbrains.plugins.gradle.ui.MatrixControlBuilder;
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
  public JComponent getControl(@NotNull ExportableOrderEntry dependency, @NotNull Collection<ExternalProjectStructureChange> changes) {
    final ProjectEntityId id = EntityIdMapper.mapEntityToId(dependency);
    final Ref<DependencyScopeChange> scopeChangeRef = new Ref<DependencyScopeChange>();
    final Ref<DependencyExportedChange> exportedChangeRef = new Ref<DependencyExportedChange>();
    ExternalProjectStructureChangeVisitor visitor = new ExternalProjectStructureChangeVisitorAdapter() {
      @Override
      public void visit(@NotNull DependencyScopeChange change) {
        if (id.equals(change.getEntityId())) {
          scopeChangeRef.set(change);
        }
      }

      @Override
      public void visit(@NotNull DependencyExportedChange change) {
        if (id.equals(change.getEntityId())) {
          exportedChangeRef.set(change);
        }
      }
    };
    for (ExternalProjectStructureChange change : changes) {
      if (scopeChangeRef.get() != null && exportedChangeRef.get() != null) {
        break;
      }
      change.invite(visitor);
    }

    final DependencyScopeChange scopeChange = scopeChangeRef.get();
    final DependencyExportedChange exportedChange = exportedChangeRef.get();
    if (scopeChange == null && exportedChange == null) {
      return null;
    }
    MatrixControlBuilder builder = GradleUtil.getConflictChangeBuilder();
    if (scopeChange != null) {
      builder.addRow(ExternalSystemBundle.message("gradle.sync.change.dependency.scope.text"),
                     scopeChange.getExternalValue(), scopeChange.getIdeValue());
    }
    if (exportedChange != null) {
      builder.addRow(ExternalSystemBundle.message("gradle.sync.change.dependency.exported.text"),
                     exportedChange.getExternalValue(), exportedChange.getIdeValue());
    }
    return builder.build();
  }
}
