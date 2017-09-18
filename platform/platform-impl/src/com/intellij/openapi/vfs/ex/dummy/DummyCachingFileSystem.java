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
package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @author gregsh
 */
public abstract class DummyCachingFileSystem<T extends VirtualFile> extends DummyFileSystem {
  private final String myProtocol;
  private final ConcurrentMap<String, T> myCachedFiles = ConcurrentFactoryMap.createMap(this::findFileByPathInner);

  public DummyCachingFileSystem(String protocol) {
    myProtocol = protocol;

    final Application application = ApplicationManager.getApplication();
    application.getMessageBus().connect(application).subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(final Project project) {
        onProjectOpened(project);
      }

      @Override
      public void projectClosed(Project project) {
        registerDisposeCallback(project);
      }
    });
    initProjectMap();
  }

  @NotNull
  @Override
  public final String getProtocol() {
    return myProtocol;
  }

  @Override
  @Nullable
  public final VirtualFile createRoot(String name) {
    return null;
  }

  @Override
  public final T findFileByPath(@NotNull String path) {
    T file = myCachedFiles.get(path);
    if (file != null && !file.isValid()) {
      myCachedFiles.remove(path);
      file = myCachedFiles.get(path);
    }
    return file;
  }

  @Override
  @NotNull
  public String extractPresentableUrl(@NotNull String path) {
    VirtualFile file = findFileByPath(path);
    return file != null ? getPresentableUrl(file) : super.extractPresentableUrl(path);
  }

  protected String getPresentableUrl(@NotNull VirtualFile file) {
    return file.getPresentableName();
  }

  protected abstract T findFileByPathInner(@NotNull String path);

  protected void doRenameFile(VirtualFile vFile, String newName) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Nullable
  public Project getProject(@Nullable String projectId) {
    Project project = ((ProjectManagerImpl)ProjectManager.getInstance()).findOpenProjectByHash(projectId);
    if (ApplicationManager.getApplication().isUnitTestMode() && project != null) {
      registerDisposeCallback(project);
      DISPOSE_CALLBACK.set(project, Boolean.TRUE);
    }
    return project;
  }

  @NotNull
  public Project getProjectOrFail(String projectId) {
    Project project = getProject(projectId);
    if (project == null) {
      throw new AssertionError(String.format("'%s' project not found among %s", projectId,
                                             StringUtil.join(ProjectManager.getInstance().getOpenProjects(), p -> p.getLocationHash(), ", ")));
    }
    return project;
  }

  @NotNull
  public Collection<T> getCachedFiles() {
    return myCachedFiles.values().stream()
      .filter(Objects::nonNull)
      .filter(VirtualFile::isValid)
      .collect(Collectors.toList());
  }

  public void onProjectClosed() {
    clearCache();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      cleanup();
    }
  }

  public void onProjectOpened(final Project project) {
    clearCache();
  }

  private static final Key<Boolean> DISPOSE_CALLBACK = Key.create("DISPOSE_CALLBACK");

  private void registerDisposeCallback(Project project) {
    if (Boolean.TRUE.equals(DISPOSE_CALLBACK.get(project))) return;
    // use Disposer instead of ProjectManagerListener#projectClosed() because Disposer.dispose(project)
    // is called later and some cached files should stay valid till the last moment
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        onProjectClosed();
      }
    });
  }

  private void initProjectMap() {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (project.isOpen()) onProjectOpened(project);
    }
  }

  protected void clearCache() {
    clearInvalidFiles();
  }

  protected void clearInvalidFiles() {
    for (Map.Entry<String, T> entry : myCachedFiles.entrySet()) {
      T t = entry.getValue();
      if (t == null || !t.isValid()) {
        myCachedFiles.remove(entry.getKey());
      }
    }
  }

  private void cleanup() {
    myCachedFiles.clear();
  }

  @Override
  public void renameFile(Object requestor, @NotNull VirtualFile vFile, @NotNull String newName) throws IOException {
    String oldName = vFile.getName();
    beforeFileRename(vFile, requestor, oldName, newName);
    try {
      doRenameFile(vFile, newName);
    }
    finally {
      fileRenamed(vFile, requestor, oldName, newName);
    }
  }

  protected void beforeFileRename(@NotNull VirtualFile file, Object requestor, @NotNull String oldName, @NotNull String newName) {
    fireBeforePropertyChange(requestor, file, VirtualFile.PROP_NAME, oldName, newName);
    myCachedFiles.remove(file.getPath());
  }

  protected void fileRenamed(@NotNull VirtualFile file, Object requestor, String oldName, String newName) {
    //noinspection unchecked
    myCachedFiles.put(file.getPath(), (T)file);
    firePropertyChanged(requestor, file, VirtualFile.PROP_NAME, oldName, newName);
  }

  protected static String escapeSlash(String name) {
    return name == null ? "" : StringUtil.replace(name, "/", "&slash;");
  }

  protected static String unescapeSlash(String name) {
    return name == null ? "" : StringUtil.replace(name, "&slash;", "/");
  }
}
