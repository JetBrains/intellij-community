// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.annotate.AnnotationGutterActionProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogApplicationSettings;
import git4idea.GitVcs;
import git4idea.annotate.GitFileAnnotation;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.config.GitVcsApplicationSettings.AnnotateDetectMovementsOption;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitToggleAnnotationOptionsActionProvider implements AnnotationGutterActionProvider {
  private static final GitVcsApplicationSettings SETTINGS = GitVcsApplicationSettings.getInstance();

  @NotNull
  @Override
  public AnAction createAction(@NotNull final FileAnnotation annotation) {
    return new MyGroup(annotation);
  }

  private static void resetAllAnnotations(@NotNull Project project) {
    ProjectLevelVcsManager.getInstance(project).getVcsHistoryCache().clearAnnotations();
    ProjectLevelVcsManager.getInstance(project).getAnnotationLocalChangesListener().reloadAnnotationsForVcs(GitVcs.getKey());
  }

  private static class MyGroup extends ActionGroup {
    private final FileAnnotation myAnnotation;

    MyGroup(@NotNull FileAnnotation annotation) {
      super(GitBundle.message("annotations.options.group"), true);
      myAnnotation = annotation;
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      if (myAnnotation instanceof GitFileAnnotation) {
        return new AnAction[]{
          new ToggleIgnoreWhitespaces(myAnnotation.getProject()),
          new ToggleInnerMovementsWhitespaces(myAnnotation.getProject()),
          new ToggleOuterMovementsWhitespaces(myAnnotation.getProject()),
          new ToggleCommitDate()
        };
      }
      return AnAction.EMPTY_ARRAY;
    }
  }

  private static final class ToggleCommitDate extends ToggleAction implements DumbAware {
    private final VcsLogApplicationSettings mySettings = ApplicationManager.getApplication().getService(VcsLogApplicationSettings.class);

    private ToggleCommitDate() {
      super(VcsBundle.messagePointer("prefer.commit.timestamp.action.text"),
            VcsBundle.messagePointer("prefer.commit.timestamp.action.description"), null);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return mySettings != null && Boolean.TRUE.equals(mySettings.get(CommonUiProperties.PREFER_COMMIT_DATE));
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      if (mySettings != null) {
        mySettings.set(CommonUiProperties.PREFER_COMMIT_DATE, state);
      }
    }
  }

  private static class ToggleIgnoreWhitespaces extends ToggleAction implements DumbAware {
    @NotNull private final Project myProject;

    ToggleIgnoreWhitespaces(@NotNull Project project) {
      super(GitBundle.message("annotations.options.ignore.whitespaces"));
      myProject = project;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return SETTINGS.isIgnoreWhitespaces();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean enabled) {
      SETTINGS.setIgnoreWhitespaces(enabled);
      resetAllAnnotations(myProject);
    }
  }

  private static class ToggleInnerMovementsWhitespaces extends ToggleAction implements DumbAware {
    @NotNull private final Project myProject;

    ToggleInnerMovementsWhitespaces(@NotNull Project project) {
      super(GitBundle.message("annotations.options.detect.movements.within.file"));
      myProject = project;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return SETTINGS.getAnnotateDetectMovementsOption() == AnnotateDetectMovementsOption.INNER ||
             SETTINGS.getAnnotateDetectMovementsOption() == AnnotateDetectMovementsOption.OUTER;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean enabled) {
      if (enabled) {
        SETTINGS.setAnnotateDetectMovementsOption(AnnotateDetectMovementsOption.INNER);
      }
      else {
        SETTINGS.setAnnotateDetectMovementsOption(AnnotateDetectMovementsOption.NONE);
      }
      resetAllAnnotations(myProject);
    }
  }

  private static class ToggleOuterMovementsWhitespaces extends ToggleAction implements DumbAware {
    @NotNull private final Project myProject;

    ToggleOuterMovementsWhitespaces(@NotNull Project project) {
      super(GitBundle.message("annotations.options.detect.movements.across.files"));
      myProject = project;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return SETTINGS.getAnnotateDetectMovementsOption() == AnnotateDetectMovementsOption.OUTER;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean enabled) {
      if (enabled) {
        SETTINGS.setAnnotateDetectMovementsOption(AnnotateDetectMovementsOption.OUTER);
      }
      else {
        SETTINGS.setAnnotateDetectMovementsOption(AnnotateDetectMovementsOption.INNER);
      }
      resetAllAnnotations(myProject);
    }
  }
}
