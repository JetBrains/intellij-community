// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.annotate.AnnotationGutterActionProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.VcsAnnotationRefresher;
import git4idea.GitVcs;
import git4idea.annotate.GitFileAnnotation;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.config.GitVcsApplicationSettings.AnnotateDetectMovementsOption;
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
    BackgroundTaskUtil.syncPublisher(project, VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED).configurationChanged(GitVcs.getKey());
  }

  private static class MyGroup extends ActionGroup {
    private final FileAnnotation myAnnotation;

    public MyGroup(@NotNull FileAnnotation annotation) {
      super("Options", true);
      myAnnotation = annotation;
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      if (myAnnotation instanceof GitFileAnnotation) {
        return new AnAction[]{
          new ToggleIgnoreWhitespaces(myAnnotation.getProject()),
          new ToggleInnerMovementsWhitespaces(myAnnotation.getProject()),
          new ToggleOuterMovementsWhitespaces(myAnnotation.getProject()),
        };
      }
      return AnAction.EMPTY_ARRAY;
    }
  }

  private static class ToggleIgnoreWhitespaces extends ToggleAction implements DumbAware {
    @NotNull private final Project myProject;

    public ToggleIgnoreWhitespaces(@NotNull Project project) {
      super("Ignore Whitespaces");
      myProject = project;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return SETTINGS.isIgnoreWhitespaces();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean enabled) {
      SETTINGS.setIgnoreWhitespaces(enabled);
      resetAllAnnotations(myProject);
    }
  }

  private static class ToggleInnerMovementsWhitespaces extends ToggleAction implements DumbAware {
    @NotNull private final Project myProject;

    public ToggleInnerMovementsWhitespaces(@NotNull Project project) {
      super("Detect Movements Within File");
      myProject = project;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return SETTINGS.getAnnotateDetectMovementsOption() == AnnotateDetectMovementsOption.INNER ||
             SETTINGS.getAnnotateDetectMovementsOption() == AnnotateDetectMovementsOption.OUTER;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean enabled) {
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

    public ToggleOuterMovementsWhitespaces(@NotNull Project project) {
      super("Detect Movements Across Files");
      myProject = project;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return SETTINGS.getAnnotateDetectMovementsOption() == AnnotateDetectMovementsOption.OUTER;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean enabled) {
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
