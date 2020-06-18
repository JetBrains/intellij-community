package com.intellij.jps.cache.action;

import com.intellij.jps.cache.client.JpsServerAuthExtension;
import com.intellij.jps.cache.loader.JpsOutputLoaderManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.intellij.execution.process.ProcessIOExecutorService.INSTANCE;

public class JpsForceUpdateCachesAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent actionEvent) {
    Project project = actionEvent.getProject();
    if (project == null) return;
    INSTANCE.execute(() -> {
      JpsServerAuthExtension.EP_NAME.extensions().findFirst().ifPresent(extension -> {
        extension.checkAuthenticated("Jps Caches Downloader", () -> JpsOutputLoaderManager.getInstance(project).load(true));
      });
    });
  }
}
