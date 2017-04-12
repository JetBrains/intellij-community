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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.indexing.AdditionalIndexableFileSet;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.UnindexedFilesUpdater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.resolve.GrabService;

import javax.swing.event.HyperlinkEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.jetbrains.plugins.groovy.grape.GrapeHelper.NOTIFICATION_GROUP;
import static org.jetbrains.plugins.groovy.grape.GrapeHelper.findGrabAnnotations;

@State(name = "GrabService", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class GrabServiceImpl implements GrabService {
  @NotNull
  public static final Logger LOG = Logger.getInstance(GrabService.class);

  @NotNull
  private final GrabClassFinder grabClassFinder;
  @NotNull
  private final Project myProject;
  @NotNull
  private final DumbService myDumbService;
  @NotNull
  private final Map<String, List<VirtualFile>> grapeState = new ConcurrentHashMap<>();
  @NotNull
  private final Map<VirtualFile, List<String>> grabs = new ConcurrentHashMap<>();
  @NotNull
  private final AtomicBoolean notified = new AtomicBoolean(false);


  public GrabServiceImpl(@NotNull Project project, @NotNull DumbService dumbService) {
    grabClassFinder = Extensions.findExtension(PsiElementFinder.EP_NAME, project, GrabClassFinder.class);
    myProject = project;
    myDumbService = dumbService;

    project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        if (myProject.isDisposed()) return;
        for (VFileEvent event: events) {
          VirtualFile file = event.getFile();
          scheduleUpdate(file);
        }
      }
    });

    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
      @Override
      public void beforeDocumentChange(@NotNull DocumentEvent event) {
      }

      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        if (myProject.isDisposed()) return;
        VirtualFile file = FileDocumentManager.getInstance().getFile(event.getDocument());
        scheduleUpdate(file);
      }
    });
  }

  private void scheduleUpdate(@Nullable VirtualFile file) {
    if (file != null && file.getFileType().equals(GroovyFileType.GROOVY_FILE_TYPE)) { //should be optimized
      scheduleUpdate(GlobalSearchScope.fileScope(myProject, file));
    }
  }

  @Override
  public void scheduleUpdate(@NotNull GlobalSearchScope scope) {
    if (myProject.isDisposed()) return;
    LOG.trace("@Grab annotations update scheduled");

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      boolean success = ProgressIndicatorUtils.runWithWriteActionPriority(() -> {
        LOG.trace("@Grab annotations update started. Scope " + scope);
        myDumbService.runReadActionInSmartMode(() -> updateGrabsInScope(scope));
        LOG.trace("@Grab annotations update finished");
      }, new ProgressIndicatorBase());
      if (!success) {
        scheduleUpdate(scope);
      }
    });
  }

  public void updateGrabsInScope(@NotNull SearchScope scope) {
    final boolean[] notify = {false};
    final boolean[] updateResolve = {false};
    Map<VirtualFile, List<String>> updateGrabQueries = collectGrabQueries(scope);
    updateGrabQueries.forEach((file, grabQueries) -> {
      for (String query : grabQueries) {
        if (grapeState.get(query) == null) notify[0] = true;
      }
      List<String> oldGrabQueries = grabs.get(file);
      if (oldGrabQueries == null || !oldGrabQueries.equals(grabQueries)) {
        grabs.put(file, Collections.unmodifiableList(grabQueries));
        updateResolve[0] = true;
      }
    });

    if (grabs.keySet().removeIf(key -> !updateGrabQueries.containsKey(key) && scope.contains(key))) {
      updateResolve[0] = true;
    }

    if (updateResolve[0]) {
      updateResolve();
    }
    if (notify[0]) {
      showNotification();
    }
  }

  private  Map<VirtualFile, List<String>> collectGrabQueries(@NotNull SearchScope scope) {
    List<PsiAnnotation> annotations = findGrabAnnotations(myProject, scope);
    Map<VirtualFile, List<String>> updateGrabQueries = new HashMap<>();

    for (PsiAnnotation annotation: annotations) {
      String grabQuery = GrapeHelper.grabQuery(annotation);

      VirtualFile file = annotation.getContainingFile().getVirtualFile();
      List<String> grabQueries = updateGrabQueries.get(file);
      if (grabQueries == null) {
        grabQueries = new ArrayList<>();
        updateGrabQueries.put(file, grabQueries);
      }
      grabQueries.add(grabQuery);
    }
    return updateGrabQueries;
  }

  private void showNotification() {
    if (!notified.compareAndSet(false, true)) return;
    String title = GroovyBundle.message("process.grab.annotations.title");
    String message = GroovyBundle.message("process.grab.annotations.message");
    NOTIFICATION_GROUP.createNotification(title, message, NotificationType.INFORMATION, new NotificationListener.Adapter() {
      @Override
      protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
        notification.expire();
        downloadProjectDependencies();
        notified.compareAndSet(true, false);
      }
    }).notify(myProject);
  }

  private void downloadProjectDependencies() {
    grapeState.clear();
    GrapeHelper.processGrabs(myProject, GlobalSearchScope.allScope(myProject), new GrapeHelper.ResultHandler() {
      @Override
      public void accept(@NotNull String grabText, @NotNull GrapeHelper.GrapeProcessHandler handler) {
        int count = handler.getJarFiles().size();
        if (count != 0) {
          final String title = count + " Grape dependency jar" + (count == 1 ? "" : "s") + " added";
          NOTIFICATION_GROUP.createNotification(title, handler.getMessages(), NotificationType.INFORMATION, null).notify(myProject);
          addDependencies(grabText, handler.getJarFiles().stream().map(VirtualFile::getPath).collect(Collectors.toList()));
        } else {
          NOTIFICATION_GROUP.createNotification(
            "Download @Grab dependency error ",
            handler.getMessages(),
            NotificationType.ERROR,
            null
          ).notify(myProject);
        }
      }
      @Override
      public void finish() {
        updateResolve();
        updateRoots();
      }
    });
  }

  @NotNull
  @Override
  public PersistentState getState() {
    return new PersistentState(grapeState);
  }

  @Override
  public void loadState(@NotNull PersistentState persistentState) {
    if (persistentState.fileMap != null) {
      persistentState.fileMap.forEach(this::addDependencies);
      updateResolve();
    }
  }

  private void addDependencies(@NotNull String grabQuery, @NotNull List<String> paths) {
    List<VirtualFile> files = new ArrayList<>();
    JarFileSystem lfs = JarFileSystem.getInstance();
    paths.forEach(path -> {
      VirtualFile file = lfs.findLocalVirtualFileByPath(path);
      if (file != null) {
        files.add(file);
      }
    });
    grapeState.put(grabQuery, Collections.unmodifiableList(files));
  }

  void updateResolve() {
    grabClassFinder.clearCache();
    ProjectRootManagerEx.getInstanceEx(myProject).clearScopesCachesForModules();
    PsiManager.getInstance(myProject).dropResolveCaches();
    ResolveCache.getInstance(myProject).clearCache(true);


  }

  void updateRoots() {
    myDumbService.queueTask(new UnindexedFilesUpdater(myProject));
  //  ApplicationManager.getApplication().invokeLater(()->
  //  ApplicationManager.getApplication().runWriteAction(()->
  //                                                     ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(EmptyRunnable.getInstance(), false, true)
  //  ));
  }

  @Override
  @NotNull
  public List<VirtualFile> getDependencies(@NotNull SearchScope scope) {
    List<VirtualFile> result = new ArrayList<>();
    grabs.forEach((file, queries) -> {
      if (scope.contains(file)) {
        result.addAll(getDependencies(queries));
      }
    });
    return result;
  }

  @Override
  public Set<VirtualFile> getJars() {
    Set<VirtualFile> jars = new HashSet<>();
    grapeState.forEach((s, files) -> {
      jars.addAll(files);
    });
    return jars;
  }

  @Override
  @NotNull
  public List<VirtualFile> getDependencies(@NotNull VirtualFile file) {

    List<String> strings = grabs.get(file);
    return strings != null ? getDependencies(strings) : Collections.emptyList();
  }

  private List<VirtualFile> getDependencies(@NotNull List<String> queries) {
    return queries.stream().map(grapeState::get).filter(Objects::nonNull).flatMap(List::stream).collect(Collectors.toList());
  }


}
