/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.components.ServiceKt;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectMacrosUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.event.HyperlinkEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class StorageUtil {
  @TestOnly
  public static String DEBUG_LOG = null;

  private StorageUtil() { }

  public static void doNotify(@NotNull final Set<String> macros, @NotNull final Project project,
                              @NotNull final Map<TrackingPathMacroSubstitutor, IComponentStore> substitutorToStore) {
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
                                     checkUnknownMacros(project, true, macros, substitutorToStore);
                                   }
                                 }, macros).notify(project);
  }

  public static void checkUnknownMacros(@NotNull Project project, boolean notify) {
    // use linked set/map to get stable results
    Set<String> unknownMacros = new LinkedHashSet<>();
    Map<TrackingPathMacroSubstitutor, IComponentStore> substitutorToStore = ContainerUtil.newLinkedHashMap();
    collect(project, unknownMacros, substitutorToStore);
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      collect(module, unknownMacros, substitutorToStore);
    }

    if (unknownMacros.isEmpty()) {
      return;
    }

    if (notify) {
      doNotify(unknownMacros, project, substitutorToStore);
      return;
    }

    checkUnknownMacros(project, false, unknownMacros, substitutorToStore);
  }

  private static void checkUnknownMacros(@NotNull Project project,
                                         boolean showDialog,
                                         @NotNull Set<String> unknownMacros,
                                         @NotNull Map<TrackingPathMacroSubstitutor, IComponentStore> substitutorToStore) {
    if (unknownMacros.isEmpty() || (showDialog && !ProjectMacrosUtil.checkMacros(project, new THashSet<>(unknownMacros)))) {
      return;
    }

    PathMacros pathMacros = PathMacros.getInstance();
    for (Iterator<String> it = unknownMacros.iterator(); it.hasNext(); ) {
      String macro = it.next();
      if (StringUtil.isEmptyOrSpaces(pathMacros.getValue(macro)) && !pathMacros.isIgnoredMacroName(macro)) {
        it.remove();
      }
    }

    if (unknownMacros.isEmpty()) {
      return;
    }

    for (Map.Entry<TrackingPathMacroSubstitutor, IComponentStore> entry : substitutorToStore.entrySet()) {
      TrackingPathMacroSubstitutor substitutor = entry.getKey();
      Set<String> components = substitutor.getComponents(unknownMacros);
      IComponentStore store = entry.getValue();
      if (store.isReloadPossible(components)) {
        substitutor.invalidateUnknownMacros(unknownMacros);

        for (UnknownMacroNotification notification : NotificationsManager.getNotificationsManager().getNotificationsOfType(UnknownMacroNotification.class, project)) {
          if (unknownMacros.containsAll(notification.getMacros())) {
            notification.expire();
          }
        }

        store.reloadStates(components, project.getMessageBus());
      }
      else if (Messages.showYesNoDialog(project, "Component could not be reloaded. Reload project?", "Configuration Changed",
                                        Messages.getQuestionIcon()) == Messages.YES) {
        ProjectManagerEx.getInstanceEx().reloadProject(project);
      }
    }
  }

  private static void collect(@NotNull ComponentManager componentManager,
                              @NotNull  Set<String> unknownMacros,
                              @NotNull Map<TrackingPathMacroSubstitutor, IComponentStore> substitutorToStore) {
    IComponentStore store = ServiceKt.getStateStore(componentManager);
    TrackingPathMacroSubstitutor substitutor = store.getStateStorageManager().getMacroSubstitutor();
    if (substitutor == null) {
      return;
    }

    Set<String> macros = substitutor.getUnknownMacros(null);
    if (macros.isEmpty()) {
      return;
    }

    unknownMacros.addAll(macros);
    substitutorToStore.put(substitutor, store);
  }

  @NotNull
  public static VirtualFile getOrCreateVirtualFile(@Nullable final Object requestor, @NotNull final Path file) throws IOException {
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(file.toString()));
    if (virtualFile != null) {
      return virtualFile;
    }
    Path absoluteFile = file.toAbsolutePath();

    Path parentFile = absoluteFile.getParent();
    Files.createDirectories(parentFile);

    // need refresh if the directory has just been created
    final VirtualFile parentVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(parentFile.toString()));
    if (parentVirtualFile == null) {
      throw new IOException(ProjectBundle.message("project.configuration.save.file.not.found", parentFile));
    }

    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      return parentVirtualFile.createChildData(requestor, file.getFileName().toString());
    }

    AccessToken token = WriteAction.start();
    try {
      return parentVirtualFile.createChildData(requestor, file.getFileName().toString());
    }
    finally {
      token.finish();
    }
  }
}
