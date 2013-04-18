package org.jetbrains.plugins.gradle.sync.conflict;

import com.intellij.openapi.externalSystem.model.project.change.*;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.externalSystem.model.project.change.LanguageLevelChange;
import org.jetbrains.plugins.gradle.ui.MatrixControlBuilder;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import java.util.Collection;

/**
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 3/15/12 4:26 PM
 */
public class GradleProjectConflictControlFactory {
  
  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  public JComponent getControl(Collection<ExternalProjectStructureChange> changes) {
    final Ref<GradleProjectRenameChange> renameChangeRef = new Ref<GradleProjectRenameChange>();
    final Ref<LanguageLevelChange> languageLevelChangeRef = new Ref<LanguageLevelChange>();
    
    ExternalProjectStructureChangeVisitor visitor = new ExternalProjectStructureChangeVisitorAdapter() {
      @Override
      public void visit(@NotNull GradleProjectRenameChange change) {
        renameChangeRef.set(change);
      }

      @Override
      public void visit(@NotNull LanguageLevelChange change) {
        languageLevelChangeRef.set(change);
      }
    };

    for (ExternalProjectStructureChange change : changes) {
      if (renameChangeRef.get() != null && languageLevelChangeRef.get() != null) {
        break;
      }
      change.invite(visitor);
    }

    final GradleProjectRenameChange renameChange = renameChangeRef.get();
    final LanguageLevelChange languageLevelChange = languageLevelChangeRef.get();
    if (renameChange == null && languageLevelChange == null) {
      return null;
    }
    
    MatrixControlBuilder builder = GradleUtil.getConflictChangeBuilder();
    if (renameChange != null) {
      builder.addRow(ExternalSystemBundle.message("gradle.import.structure.settings.label.name"),
                     renameChange.getExternalValue(), renameChange.getIdeValue());
    }

    if (languageLevelChange != null) {
      builder.addRow(ExternalSystemBundle.message("gradle.import.structure.settings.label.language.level"),
                     getTextToShow(languageLevelChange.getExternalValue()), getTextToShow(languageLevelChange.getIdeValue()));
    }
    
    return builder.build();
  }

  @NotNull
  private static String getTextToShow(@NotNull LanguageLevel level) {
    final String s = level.toString();
    return s.substring(s.indexOf('1')).replace('_', '.');
  }
}
