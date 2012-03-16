package org.jetbrains.plugins.gradle.sync.conflict;

import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitor;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitorAdapter;
import org.jetbrains.plugins.gradle.diff.project.GradleLanguageLevelChange;
import org.jetbrains.plugins.gradle.diff.project.GradleProjectRenameChange;
import org.jetbrains.plugins.gradle.ui.MatrixControlBuilder;
import org.jetbrains.plugins.gradle.util.GradleBundle;
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
  public JComponent getControl(Collection<GradleProjectStructureChange> changes) {
    final Ref<GradleProjectRenameChange> renameChangeRef = new Ref<GradleProjectRenameChange>();
    final Ref<GradleLanguageLevelChange> languageLevelChangeRef = new Ref<GradleLanguageLevelChange>();
    
    GradleProjectStructureChangeVisitor visitor = new GradleProjectStructureChangeVisitorAdapter() {
      @Override
      public void visit(@NotNull GradleProjectRenameChange change) {
        renameChangeRef.set(change);
      }

      @Override
      public void visit(@NotNull GradleLanguageLevelChange change) {
        languageLevelChangeRef.set(change);
      }
    };

    for (GradleProjectStructureChange change : changes) {
      if (renameChangeRef.get() != null && languageLevelChangeRef.get() != null) {
        break;
      }
      change.invite(visitor);
    }

    final GradleProjectRenameChange renameChange = renameChangeRef.get();
    final GradleLanguageLevelChange languageLevelChange = languageLevelChangeRef.get();
    if (renameChange == null && languageLevelChange == null) {
      return null;
    }
    
    MatrixControlBuilder builder = GradleUtil.getConflictChangeBuilder();
    if (renameChange != null) {
      builder.addRow(GradleBundle.message("gradle.import.structure.settings.label.name"),
                     renameChange.getGradleValue(), renameChange.getIntellijValue());
    }

    if (languageLevelChange != null) {
      builder.addRow(GradleBundle.message("gradle.import.structure.settings.label.language.level"),
                     getTextToShow(languageLevelChange.getGradleValue()), getTextToShow(languageLevelChange.getIntellijValue()));
    }
    
    return builder.build();
  }

  @NotNull
  private static String getTextToShow(@NotNull LanguageLevel level) {
    final String s = level.toString();
    return s.substring(s.indexOf('1')).replace('_', '.');
  }
}
