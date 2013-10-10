package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

/**
 * Common super class for gradle actions that require {@link GradleSettings#getLinkedExternalProjectPath()}  linked project}.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/31/12 5:36 PM
 */
public abstract class AbstractGradleLinkedProjectAction extends AnAction {

  @Override
  public void update(AnActionEvent e) {
    final Pair<Project, String> pair = deriveProjects(e.getDataContext());
    final boolean visible = pair != null;
    e.getPresentation().setVisible(visible);
    if (!visible) {
      return;
    }
    doUpdate(e, pair.first, pair.second);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      return;
    }
    final Pair<Project, String> pair = deriveProjects(e.getDataContext());
    if (pair == null) {
      e.getPresentation().setVisible(false);
      return;
    }
    doActionPerformed(e, project, pair.second);
  }

  @Nullable
  private static Pair<Project, String> deriveProjects(@Nullable DataContext context) {
    if (context == null) {
      return null;
    }
    
    final Project project = CommonDataKeys.PROJECT.getData(context);
    if (project == null) {
      return null;
    }
    // TODO den implement
   return null; 
//    final String path = GradleSettings.getInstance(project).getLinkedExternalProjectPath();
//    return path == null ? null : new Pair<Project, String>(project, path);
  }

  protected abstract void doUpdate(@NotNull AnActionEvent event, @NotNull Project project, @NotNull String linkedProjectPath);
  protected abstract void doActionPerformed(@NotNull AnActionEvent event, @NotNull Project project, @NotNull String linkedProjectPath);
}
