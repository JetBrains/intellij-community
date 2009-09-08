package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

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

  public FileStatus calcStatus(@NotNull VirtualFile virtualFile) {
    if (virtualFile.isInLocalFileSystem() && myFileStatusProvider != null) {
      return myFileStatusProvider.getFileStatus(virtualFile);
    } else {
      return FileStatus.NOT_CHANGED;
    }
  }

  public void projectClosed() {
  }

  public void projectOpened() {
    MyDocumentAdapter documentListener = new MyDocumentAdapter();
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(documentListener, myProject);
  }

  public void disposeComponent() {
    myCachedStatuses.clear();
  }

  @NotNull
  public String getComponentName() {
    return "FileStatusManager";
  }

  public void initComponent() { }

  public void addFileStatusListener(FileStatusListener listener) {
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

    if (!file.isValid()) return;
    FileStatus cachedStatus = getCachedStatus(file);
    if (cachedStatus == null) return;
    FileStatus newStatus = calcStatus(file);
    if (cachedStatus == newStatus) return;
    myCachedStatuses.put(file, newStatus);

    for (FileStatusListener listener : myListeners) {
      listener.fileStatusChanged(file);
    }
  }

  public FileStatus getStatus(final VirtualFile file) {
    FileStatus status = getCachedStatus(file);
    if (status == null) {
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

  private class MyDocumentAdapter extends DocumentAdapter {
    public void documentChanged(DocumentEvent event) {
      VirtualFile file = FileDocumentManager.getInstance().getFile(event.getDocument());
      if (file != null) {
        refreshFileStatusFromDocument(file, event.getDocument());
      }
    }
  }
}
