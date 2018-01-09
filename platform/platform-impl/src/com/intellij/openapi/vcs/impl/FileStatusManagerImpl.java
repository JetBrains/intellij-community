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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author mike
 */
public class FileStatusManagerImpl extends FileStatusManager implements ProjectComponent, Disposable {
  private static final Logger LOG = Logger.getInstance(FileStatusManagerImpl.class);
  private final Map<VirtualFile, FileStatus> myCachedStatuses = Collections.synchronizedMap(new HashMap<VirtualFile, FileStatus>());
  private final Map<VirtualFile, Boolean> myWhetherExactlyParentToChanged =
    Collections.synchronizedMap(new HashMap<VirtualFile, Boolean>());
  private final Project myProject;
  private final List<FileStatusListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private FileStatusProvider myFileStatusProvider;
  private final NotNullLazyValue<FileStatusProvider[]> myExtensions = new NotNullLazyValue<FileStatusProvider[]>() {
    @NotNull
    @Override
    protected FileStatusProvider[] compute() {
      return Extensions.getExtensions(FileStatusProvider.EP_NAME, myProject);
    }
  };

  private static class FileStatusNull implements FileStatus {
    private static final FileStatus INSTANCE = new FileStatusNull();

    @Override
    public String getText() {
      throw new AssertionError("Should not be called");
    }

    @Override
    public Color getColor() {
      throw new AssertionError("Should not be called");
    }

    @NotNull
    @Override
    public ColorKey getColorKey() {
      throw new AssertionError("Should not be called");
    }

    @NotNull
    @Override
    public String getId() {
      throw new AssertionError("Should not be called");
    }
  }

  public FileStatusManagerImpl(Project project, StartupManager startupManager, @SuppressWarnings("UnusedParameters") DirectoryIndex makeSureIndexIsInitializedFirst) {
    myProject = project;

    project.getMessageBus().connect().subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
      @Override
      public void globalSchemeChange(EditorColorsScheme scheme) {
        fileStatusesChanged();
      }
    });

    if (project.isDefault()) return;

    startupManager.registerPreStartupActivity(() -> {
      final EditorFactory factory = EditorFactory.getInstance();
      if (factory != null) {
        factory.getEventMulticaster().addDocumentListener(new DocumentListener() {
          @Override
          public void documentChanged(DocumentEvent event) {
            if (event.getOldLength() == 0 && event.getNewLength() == 0) return;
            VirtualFile file = FileDocumentManager.getInstance().getFile(event.getDocument());
            if (file != null) {
              refreshFileStatusFromDocument(file, event.getDocument());
            }
          }
        }, myProject);
      }
    });
    startupManager.registerPostStartupActivity((DumbAwareRunnable)() -> fileStatusesChanged());
  }

  public void setFileStatusProvider(final FileStatusProvider fileStatusProvider) {
    myFileStatusProvider = fileStatusProvider;
  }

  public FileStatus calcStatus(@NotNull final VirtualFile virtualFile) {
    for (FileStatusProvider extension : myExtensions.getValue()) {
      final FileStatus status = extension.getFileStatus(virtualFile);
      if (status != null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(String.format("File status for file [%s] from provider %s: %s", virtualFile, extension.getClass().getName(), status));
        }
        return status;
      }
    }

    if (virtualFile.isInLocalFileSystem() && myFileStatusProvider != null) {
      FileStatus status = myFileStatusProvider.getFileStatus(virtualFile);
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("File status for file [%s] from default provider %s: %s", virtualFile, myFileStatusProvider, status));
      }
      return status;
    }

    FileStatus defaultStatus = getDefaultStatus(virtualFile);
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("Default status for file [%s]: %s", virtualFile, defaultStatus));
    }
    return defaultStatus;
  }

  @NotNull
  public static FileStatus getDefaultStatus(@NotNull final VirtualFile file) {
    return file.isValid() && file.is(VFileProperty.SPECIAL) ? FileStatus.IGNORED : FileStatus.NOT_CHANGED;
  }

  @Override
  public void dispose() {
    myCachedStatuses.clear();
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "FileStatusManager";
  }

  @Override
  public void addFileStatusListener(@NotNull FileStatusListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void addFileStatusListener(@NotNull final FileStatusListener listener, @NotNull Disposable parentDisposable) {
    addFileStatusListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        removeFileStatusListener(listener);
      }
    });
  }

  @Override
  public void fileStatusesChanged() {
    if (myProject.isDisposed()) {
      return;
    }
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().invokeLater((DumbAwareRunnable)() -> fileStatusesChanged(), ModalityState.any());
      return;
    }

    myCachedStatuses.clear();
    myWhetherExactlyParentToChanged.clear();

    for (FileStatusListener listener : myListeners) {
      listener.fileStatusesChanged();
    }
  }

  private void cacheChangedFileStatus(final VirtualFile virtualFile, final FileStatus fs) {
    myCachedStatuses.put(virtualFile, fs);
    if (FileStatus.NOT_CHANGED.equals(fs)) {
      final ThreeState parentingStatus = myFileStatusProvider.getNotChangedDirectoryParentingStatus(virtualFile);
      if (ThreeState.YES.equals(parentingStatus)) {
        myWhetherExactlyParentToChanged.put(virtualFile, true);
      }
      else if (ThreeState.UNSURE.equals(parentingStatus)) {
        myWhetherExactlyParentToChanged.put(virtualFile, false);
      }
    }
    else {
      myWhetherExactlyParentToChanged.remove(virtualFile);
    }
  }

  @Override
  public void fileStatusChanged(final VirtualFile file) {
    final Application application = ApplicationManager.getApplication();
    if (!application.isDispatchThread() && !application.isUnitTestMode()) {
      ApplicationManager.getApplication().invokeLater((DumbAwareRunnable)() -> fileStatusChanged(file));
      return;
    }

    if (file == null || !file.isValid()) return;
    FileStatus cachedStatus = getCachedStatus(file);
    if (cachedStatus == FileStatusNull.INSTANCE) {
      return;
    }
    if (cachedStatus == null) {
      cacheChangedFileStatus(file, FileStatusNull.INSTANCE);
      return;
    }
    FileStatus newStatus = calcStatus(file);
    if (cachedStatus == newStatus) return;
    cacheChangedFileStatus(file, newStatus);

    for (FileStatusListener listener : myListeners) {
      listener.fileStatusChanged(file);
    }
  }

  @Override
  public FileStatus getStatus(@NotNull final VirtualFile file) {
    if (file.getFileSystem() instanceof NonPhysicalFileSystem) {
      return FileStatus.SUPPRESSED;  // do not leak light files via cache
    }

    FileStatus status = getCachedStatus(file);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Cached status for file [" + file + "] is " + status);
    }
    if (status == null || status == FileStatusNull.INSTANCE) {
      status = calcStatus(file);
      cacheChangedFileStatus(file, status);
    }

    return status;
  }

  public FileStatus getCachedStatus(final VirtualFile file) {
    return myCachedStatuses.get(file);
  }

  @Override
  public void removeFileStatusListener(@NotNull FileStatusListener listener) {
    myListeners.remove(listener);
  }

  @Override
  public Color getNotChangedDirectoryColor(@NotNull VirtualFile vf) {
    final Color notChangedColor = FileStatus.NOT_CHANGED.getColor();
    if (!vf.isDirectory()) {
      return notChangedColor;
    }
    final Boolean exactMatch = myWhetherExactlyParentToChanged.get(vf);
    return exactMatch == null
           ? notChangedColor
           : exactMatch ? FileStatus.NOT_CHANGED_IMMEDIATE.getColor() : FileStatus.NOT_CHANGED_RECURSIVE.getColor();
  }

  public void refreshFileStatusFromDocument(final VirtualFile file, final Document doc) {
    if (myFileStatusProvider != null) {
      myFileStatusProvider.refreshFileStatusFromDocument(file, doc);
    }
  }
}
