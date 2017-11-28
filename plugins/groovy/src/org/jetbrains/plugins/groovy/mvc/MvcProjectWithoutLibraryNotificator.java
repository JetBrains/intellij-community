/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class MvcProjectWithoutLibraryNotificator implements StartupActivity, DumbAware {
  @Override
  public void runActivity(@NotNull final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    ProgressIndicatorUtils.scheduleWithWriteActionPriority(new ReadTask() {
      @Override
      public void computeInReadAction(@NotNull ProgressIndicator indicator) {
        if (project.isDisposed()) return;
        final Pair<Module, MvcFramework> pair = findModuleWithoutLibrary(project);
        if (pair == null) return;

        final MvcFramework framework = pair.second;
        final Module module = pair.first;
        final String name = framework.getFrameworkName();
        final Map<String, Runnable> actions = framework.createConfigureActions(module);

        final StringBuilder content = new StringBuilder()
          .append("<html><body>")
          .append("Module ").append('\'').append(module.getName()).append('\'')
          .append(" has no ").append(name).append(" SDK.");
        if (!actions.isEmpty()) content.append("<br/>");
        content.append(StringUtil.join(actions.keySet(), actionName -> String.format("<a href='%s'>%s</a>", actionName, actionName), " "));
        content.append("</body></html>");

        new Notification(
          name + ".Configure", name + " SDK not found", content.toString(), NotificationType.INFORMATION,
          new NotificationListener.Adapter() {
            @Override
            protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
              if (module.isDisposed()) return;
              final Runnable runnable = actions.get(e.getDescription());
              assert runnable != null;
              runnable.run();
            }
          }
        ).notify(project);
      }

      @Override
      public Continuation runBackgroundProcess(@NotNull final ProgressIndicator indicator) {
        return DumbService.getInstance(project).runReadActionInSmartMode(() -> performInReadAction(indicator));
      }

      @Override
      public void onCanceled(@NotNull ProgressIndicator indicator) {
        if (!project.isDisposed()) {
          ProgressIndicatorUtils.scheduleWithWriteActionPriority(this);
        }
      }
    });
  }

  @Nullable
  private static Pair<Module, MvcFramework> findModuleWithoutLibrary(Project project) {
    MvcFramework[] frameworks = MvcFramework.EP_NAME.getExtensions();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      for (MvcFramework framework : frameworks) {
        if (framework.hasFrameworkStructure(module) && !framework.hasFrameworkJar(module)) {
          if (VfsUtil.findRelativeFile(framework.findAppRoot(module), "application.properties") != null) {
            return Pair.create(module, framework);
          }
        }
      }
    }

    return null;
  }
}
