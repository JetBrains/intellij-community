/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.components.impl.stores;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.application.*;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.ComponentsPackage;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectMacrosUtil;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class StorageUtil {
  public static final String DEFAULT_EXT = ".xml";

  private static final Logger LOG = Logger.getInstance(StorageUtil.class);

  @TestOnly
  public static String DEBUG_LOG = null;

  private StorageUtil() { }

  public static void checkUnknownMacros(@NotNull final ComponentManager componentManager, @NotNull final Project project) {
    Application application = ApplicationManager.getApplication();
    if (application.isHeadlessEnvironment() || application.isUnitTestMode()) {
      return;
    }

    // should be invoked last
    StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
      @Override
      public void run() {
        notifyUnknownMacros(ComponentsPackage.getStateStore(componentManager), project, null);
      }
    });
  }

  public static void notifyUnknownMacros(@NotNull final IComponentStore store, @NotNull final Project project, @Nullable final String componentName) {
    TrackingPathMacroSubstitutor substitutor = store.getStateStorageManager().getMacroSubstitutor();
    if (substitutor == null) {
      return;
    }

    final LinkedHashSet<String> macros = new LinkedHashSet<String>(substitutor.getUnknownMacros(componentName));
    if (macros.isEmpty()) {
      return;
    }

    AppUIUtil.invokeOnEdt(new Runnable() {
      @Override
      public void run() {
        List<String> notified = null;
        NotificationsManager manager = NotificationsManager.getNotificationsManager();
        for (UnknownMacroNotification notification : manager.getNotificationsOfType(UnknownMacroNotification.class, project)) {
          if (notified == null) {
            notified = new SmartList<String>();
          }
          notified.addAll(notification.getMacros());
        }
        if (!ContainerUtil.isEmpty(notified)) {
          macros.removeAll(notified);
        }

        if (macros.isEmpty()) {
          return;
        }

        LOG.debug("Reporting unknown path macros " + macros + " in component " + componentName);
        String format = "<p><i>%s</i> %s undefined. <a href=\"define\">Fix it</a></p>";
        String productName = ApplicationNamesInfo.getInstance().getProductName();
        String content = String.format(format, StringUtil.join(macros, ", "), macros.size() == 1 ? "is" : "are") +
                         "<br>Path variables are used to substitute absolute paths " +
                         "in " + productName + " project files " +
                         "and allow project file sharing in version control systems.<br>" +
                         "Some of the files describing the current project settings contain unknown path variables " +
                         "and " + productName + " cannot restore those paths.";
        new UnknownMacroNotification("Load Error", "Load error: undefined path variables", content, NotificationType.ERROR,
                                     new NotificationListener() {
                                       @Override
                                       public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
                                         checkUnknownMacros(store, project, true);
                                       }
                                     }, macros).notify(project);
      }
    }, project.getDisposed());
  }

  public static void checkUnknownMacros(@NotNull IComponentStore store, @NotNull Project project, boolean showDialog) {
    // default project doesn't have it
    List<TrackingPathMacroSubstitutor> substitutors;
    if (store instanceof IProjectStore) {
      substitutors = ((IProjectStore)store).getSubstitutors();
    }
    else {
      substitutors = Collections.emptyList();
    }
    Set<String> unknownMacros = new THashSet<String>();
    for (TrackingPathMacroSubstitutor substitutor : substitutors) {
      unknownMacros.addAll(substitutor.getUnknownMacros(null));
    }

    if (unknownMacros.isEmpty() || showDialog && !ProjectMacrosUtil.checkMacros(project, new THashSet<String>(unknownMacros))) {
      return;
    }

    final PathMacros pathMacros = PathMacros.getInstance();
    final Set<String> macrosToInvalidate = new THashSet<String>(unknownMacros);
    for (Iterator<String> it = macrosToInvalidate.iterator(); it.hasNext(); ) {
      String macro = it.next();
      if (StringUtil.isEmptyOrSpaces(pathMacros.getValue(macro)) && !pathMacros.isIgnoredMacroName(macro)) {
        it.remove();
      }
    }

    if (macrosToInvalidate.isEmpty()) {
      return;
    }

    Set<String> components = new THashSet<String>();
    for (TrackingPathMacroSubstitutor substitutor : substitutors) {
      components.addAll(substitutor.getComponents(macrosToInvalidate));
    }

    if (store.isReloadPossible(components)) {
      for (TrackingPathMacroSubstitutor substitutor : substitutors) {
        substitutor.invalidateUnknownMacros(macrosToInvalidate);
      }

      for (UnknownMacroNotification notification : NotificationsManager.getNotificationsManager().getNotificationsOfType(UnknownMacroNotification.class, project)) {
        if (macrosToInvalidate.containsAll(notification.getMacros())) {
          notification.expire();
        }
      }

      store.reloadStates(components, project.getMessageBus());
    }
    else if (Messages.showYesNoDialog(project, "Component could not be reloaded. Reload project?", "Configuration Changed", Messages.getQuestionIcon()) == Messages.YES) {
      ProjectManagerEx.getInstanceEx().reloadProject(project);
    }
  }

  @NotNull
  public static VirtualFile getOrCreateVirtualFile(@Nullable final Object requestor, @NotNull final File file) throws IOException {
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    if (virtualFile != null) {
      return virtualFile;
    }
    File absoluteFile = file.getAbsoluteFile();
    FileUtil.createParentDirs(absoluteFile);

    File parentFile = absoluteFile.getParentFile();
    // need refresh if the directory has just been created
    final VirtualFile parentVirtualFile = StringUtil.isEmpty(parentFile.getPath()) ? null : LocalFileSystem.getInstance().refreshAndFindFileByIoFile(parentFile);
    if (parentVirtualFile == null) {
      throw new IOException(ProjectBundle.message("project.configuration.save.file.not.found", parentFile));
    }

    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      return parentVirtualFile.createChildData(requestor, file.getName());
    }

    AccessToken token = WriteAction.start();
    try {
      return parentVirtualFile.createChildData(requestor, file.getName());
    }
    finally {
      token.finish();
    }
  }
}
