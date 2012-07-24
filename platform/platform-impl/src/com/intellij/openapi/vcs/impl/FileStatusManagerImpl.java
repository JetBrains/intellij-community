/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
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
public class FileStatusManagerImpl extends FileStatusManager implements ProjectComponent {
  private final Map<VirtualFile, FileStatus> myCachedStatuses = Collections.synchronizedMap(new HashMap<VirtualFile, FileStatus>());
  private final Project myProject;
  private final List<FileStatusListener> myListeners = ContainerUtil.createEmptyCOWList();
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

    @Override public String getText() {
      throw new AssertionError("Should not be called");
    }

    @Override public Color getColor() {
      throw new AssertionError("Should not be called");
    }

    @Override public ColorKey getColorKey() {
      throw new AssertionError("Should not be called");
    }
  }

  public FileStatusManagerImpl(Project project, StartupManager startupManager) {
    myProject = project;

    startupManager.registerPostStartupActivity(new DumbAwareRunnable() {
      public void run() {
        fileStatusesChanged();
      }
    });
  }

  public void setFileStatusProvider(final FileStatusProvider fileStatusProvider) {
    myFileStatusProvider = fileStatusProvider;
  }

  public FileStatus calcStatus(@NotNull final VirtualFile virtualFile) {
    for (FileStatusProvider extension : myExtensions.getValue()) {
      final FileStatus status = extension.getFileStatus(virtualFile);
      if (status != null) {
        return status;
      }
    }

    if (virtualFile.isInLocalFileSystem() && myFileStatusProvider != null) {
      return myFileStatusProvider.getFileStatus(virtualFile);
    }

    return getDefaultStatus(virtualFile);
  }

  public static FileStatus getDefaultStatus(@NotNull final VirtualFile file) {
    return file.isValid() && file.isSpecialFile() ? FileStatus.IGNORED : FileStatus.NOT_CHANGED;
  }

  public void projectClosed() {
  }

  public void projectOpened() {
    DocumentAdapter documentListener = new DocumentAdapter() {
      public void documentChanged(DocumentEvent event) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(event.getDocument());
        if (file != null) {
          refreshFileStatusFromDocument(file, event.getDocument());
        }
      }
    };

    final EditorFactory factory = EditorFactory.getInstance();
    if (factory != null) {
      factory.getEventMulticaster().addDocumentListener(documentListener, myProject);
    }
  }

  public void disposeComponent() {
    myCachedStatuses.clear();
  }

  @NotNull
  public String getComponentName() {
    return "FileStatusManager";
  }

  public void initComponent() { }

  public void addFileStatusListener(@NotNull FileStatusListener listener) {
    myListeners.add(listener);
  }

  public void addFileStatusListener(final FileStatusListener listener, Disposable parentDisposable) {
    addFileStatusListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeFileStatusListener(listener);
      }
    });
  }

  public void fileStatusesChanged() {
    if (myProject.isDisposed()) {
      return;
    }
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
        public void run() {
          fileStatusesChanged();
        }
      }, ModalityState.NON_MODAL);
      return;
    }

    myCachedStatuses.clear();

    for (FileStatusListener listener : myListeners) {
      listener.fileStatusesChanged();
    }
  }

  public void fileStatusChanged(final VirtualFile file) {
    final Application application = ApplicationManager.getApplication();
    if (!application.isDispatchThread() && !application.isUnitTestMode()) {
      ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
        public void run() {
          fileStatusChanged(file);
        }
      });
      return;
    }

    if ((file == null) || (! file.isValid())) return;
    FileStatus cachedStatus = getCachedStatus(file);
    if (cachedStatus == FileStatusNull.INSTANCE) {
      return;
    }
    if (cachedStatus == null) {
      myCachedStatuses.put(file, FileStatusNull.INSTANCE);
      return;
    }
    FileStatus newStatus = calcStatus(file);
    if (cachedStatus == newStatus) return;
    myCachedStatuses.put(file, newStatus);

    for (FileStatusListener listener : myListeners) {
      listener.fileStatusChanged(file);
    }
  }

  public FileStatus getStatus(final VirtualFile file) {
    if (file instanceof LightVirtualFile) {
      return FileStatus.NOT_CHANGED;  // do not leak light files via cache
    }

    FileStatus status = getCachedStatus(file);
    if (status == null || status == FileStatusNull.INSTANCE) {
      status = calcStatus(file);
      myCachedStatuses.put(file, status);
    }

    return status;
  }

  public FileStatus getCachedStatus(final VirtualFile file) {
    return myCachedStatuses.get(file);
  }

  public void removeFileStatusListener(FileStatusListener listener) {
    myListeners.remove(listener);
  }

  public void refreshFileStatusFromDocument(final VirtualFile file, final Document doc) {
    if (myFileStatusProvider != null) {
      myFileStatusProvider.refreshFileStatusFromDocument(file, doc);
    }
  }
}
