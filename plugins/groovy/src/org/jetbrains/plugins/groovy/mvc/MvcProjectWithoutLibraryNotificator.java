package org.jetbrains.plugins.groovy.mvc;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;

/**
 * @author Sergey Evdokimov
 */
public class MvcProjectWithoutLibraryNotificator implements StartupActivity, DumbAware {

  @Override
  public void runActivity(@NotNull final Project project) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();

        try {
          if (JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(project)) == null) {
            return; // If indexes is corrupted JavaPsiFacade.findClass() can't find classes during StartupActivity (may be it's a bug).
                    // So we can't determine whether exists Grails library or not.
          }

          Pair<Module, MvcFramework> pair = findModuleWithoutLibrary(project);

          if (pair != null) {
            final MvcFramework framework = pair.second;
            final Module module = pair.first;

            new Notification(framework.getFrameworkName() + ".Configure",
                             framework.getFrameworkName() + " SDK not found.",
                             "<html><body>Module '" +
                             module.getName() +
                             "' has no " +
                             framework.getFrameworkName() +
                             " SDK. <a href='create'>Configure SDK</a></body></html>", NotificationType.INFORMATION,
                             new NotificationListener.Adapter() {
                               @Override
                               protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
                                 MvcConfigureNotification.configure(framework, module);
                               }
                             }).notify(project);
          }
        }
        finally {
          accessToken.finish();
        }
      }
    });
  }

  @Nullable
  private static Pair<Module, MvcFramework> findModuleWithoutLibrary(Project project) {
    MvcFramework[] frameworks = MvcFramework.EP_NAME.getExtensions();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      for (MvcFramework framework : frameworks) {
        VirtualFile appRoot = framework.findAppRoot(module);
        if (appRoot != null && appRoot.findChild("application.properties") != null) {
           if (!framework.hasFrameworkJar(module)) {
             return Pair.create(module, framework);
           }
        }
      }
    }

    return null;
  }
}
