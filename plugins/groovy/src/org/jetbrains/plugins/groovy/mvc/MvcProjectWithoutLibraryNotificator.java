// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.swing.event.HyperlinkEvent;

/**
 * @author Sergey Evdokimov
 */
public class MvcProjectWithoutLibraryNotificator implements StartupActivity.DumbAware {
  @Override
  public void runActivity(@NotNull final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    ReadAction.nonBlocking(() -> {
      final Pair<Module, MvcFramework> pair = findModuleWithoutLibrary(project);
      if (pair == null) return;

      final MvcFramework framework = pair.second;
      final Module module = pair.first;
      final String name = framework.getFrameworkName();
      final var actions = framework.createConfigureActions(module);

      HtmlBuilder builder = new HtmlBuilder();
      builder.append(GroovyBundle.message("mvc.framework.0.module.1.has.no.sdk", name, module.getName()));
      if (!actions.isEmpty()) builder.br();
      for (var actionName : actions.keySet()) {
        builder.appendLink(actionName, actionName).append(" ");
      }
      String message = builder.wrapWithHtmlBody().toString();

      new Notification(
        name + ".Configure", GroovyBundle.message("mvc.framework.0.sdk.not.found.title", name), message, NotificationType.INFORMATION,
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
    }).inSmartMode(project).submit(AppExecutorUtil.getAppExecutorService());
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
