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
package org.jetbrains.plugins.groovy.grape;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.swing.event.HyperlinkEvent;
import java.util.*;

import static org.jetbrains.plugins.groovy.grape.GrapeHelper.NOTIFICATION_GROUP;

@State(name = "GrabService", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class GrabService implements PersistentStateComponent<GrabService.PersistentState> {
  private final GrabClassFinder grabClassFinder;
  public HashMap<String, List<String>> dependencyMap = new HashMap<>();


  public GrabService(@NotNull Project project) {
    grabClassFinder = Extensions.findExtension(PsiElementFinder.EP_NAME, project, GrabClassFinder.class);
    /*Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        cancelIndicator();
      }
    });*/
    project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {

    });
  }

  @NotNull
  public static GrabService getInstance(@NotNull Project project) {
    return ObjectUtils.notNull(ServiceManager.getService(project, GrabService.class));
  }
/*
  private void cancelIndicator() {
    ProgressIndicator indicator = myIndicator;
    if (indicator != null) {
      indicator.cancel();
      myIndicator = null;
    }
  }
*/
  public static void showNotification(@NotNull Project project) {
    String title = GroovyBundle.message("process.grab.annotations.title");
    String message = GroovyBundle.message("process.grab.annotations.message");
    NOTIFICATION_GROUP.createNotification(title, message, NotificationType.INFORMATION, new NotificationListener.Adapter() {
      @Override
      protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
        GrapeHelper.processProject(project);
      }
    }).notify(project);
  }

  @NotNull
  @Override
  public PersistentState getState() {
    return new PersistentState(dependencyMap);
  }

  @Override
  public void loadState(@NotNull PersistentState persistentState) {
    persistentState.fileMap.forEach( (key, value) -> dependencyMap.put(key, Collections.unmodifiableList(value)));
    grabClassFinder.clearCache();
  }

  public void clearState() {
    dependencyMap.clear();
    grabClassFinder.clearCache();
  }

  public void addDependencies(@NotNull String grabQuery, @NotNull List<String> paths) {
    dependencyMap.put(grabQuery, Collections.unmodifiableList(paths));
    grabClassFinder.clearCache();
  }

  @Nullable
  public List<String> getPaths(@NotNull String grabQuery) {
    return dependencyMap.get(grabQuery);
  }

  @NotNull
  public static List<VirtualFile> getRoots(@NotNull Project project) {
    List<VirtualFile> files = new ArrayList<>();
    JarFileSystem lfs = JarFileSystem.getInstance();
    getInstance(project).getState().getFileMap().forEach((key, value) -> value.forEach(path -> {

      VirtualFile file = lfs.findLocalVirtualFileByPath(path);
      if (file != null) {
        files.add(file);
      }
    }));
    return files;
  }

  @Nullable
  public static List<VirtualFile> getRootsByAnnotation(@NotNull PsiAnnotation annotation) {
    String grabQuery = GrapeHelper.grabQuery(annotation);
    List<String> paths = getInstance(annotation.getProject()).getPaths(grabQuery);
    if (paths == null) {
      showNotification(annotation.getProject());
      return null;
    }
    List<VirtualFile> files = new ArrayList<>();
    JarFileSystem lfs = JarFileSystem.getInstance();
    paths.forEach(path -> {
      VirtualFile file = lfs.findLocalVirtualFileByPath(path);
      if (file != null) {
        files.add(file);
      }
    });
    return files;
  }

  @NotNull
  public static List<VirtualFile> getRootsByScope(@NotNull Project project, @NotNull SearchScope scope) {
    List<VirtualFile> result = new ArrayList<>();
    GrapeHelper.findGrabAnnotations(project, scope).forEach(annotation -> {
      List<VirtualFile> roots = getRootsByAnnotation(annotation);
      if (roots != null) result.addAll(roots);
    });

    return result;
  }

  public static class PersistentState {
    public Map<String, List<String>> fileMap;

    public PersistentState(HashMap<String, List<String>> map) {
      fileMap = new HashMap<>(map);
    }

    @SuppressWarnings("unused")
    public PersistentState() {
    }

    @NotNull
    public Map<String, List<String>> getFileMap() {
      return new HashMap<>(fileMap);
    }
  }
}
