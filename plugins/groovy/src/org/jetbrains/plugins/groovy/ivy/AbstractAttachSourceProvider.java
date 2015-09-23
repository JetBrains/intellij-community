package org.jetbrains.plugins.groovy.ivy;

import com.intellij.codeInsight.AttachSourcesProvider;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public abstract class AbstractAttachSourceProvider implements AttachSourcesProvider {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.ivy.AbstractAttachSourceProvider");

  @Nullable
  protected static VirtualFile getJarByPsiFile(PsiFile psiFile) {
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return null;

    VirtualFile jar = JarFileSystem.getInstance().getVirtualFileForJar(psiFile.getVirtualFile());

    if (jar == null || !jar.getName().endsWith(".jar")) return null;

    return jar;
  }

  @Nullable
  protected static Library getLibraryFromOrderEntriesList(List<LibraryOrderEntry> orderEntries) {
    if (orderEntries.isEmpty()) return null;

    Library library = orderEntries.get(0).getLibrary();
    if (library == null) return null;

    for (int i = 1; i < orderEntries.size(); i++) {
      if (!library.equals(orderEntries.get(i).getLibrary())) {
        return null;
      }
    }

    return library;
  }

  protected void addSourceFile(@Nullable VirtualFile jarRoot, @NotNull Library library) {
    if (jarRoot != null) {
      if (!Arrays.asList(library.getFiles(OrderRootType.SOURCES)).contains(jarRoot)) {
        Library.ModifiableModel model = library.getModifiableModel();
        model.addRoot(jarRoot, OrderRootType.SOURCES);
        model.commit();
      }
    }
  }

  protected class AttachExistingSourceAction implements AttachSourcesAction {
    private final String myName;
    private final VirtualFile mySrcFile;
    private final Library myLibrary;

    public AttachExistingSourceAction(VirtualFile srcFile, Library library, String actionName) {
      mySrcFile = srcFile;
      myLibrary = library;
      myName = actionName;
    }

    @Override
    public String getName() {
      return myName;
    }

    @Override
    public String getBusyText() {
      return getName();
    }

    @Override
    public ActionCallback perform(List<LibraryOrderEntry> orderEntriesContainingFile) {
      ApplicationManager.getApplication().assertIsDispatchThread();

      ActionCallback callback = new ActionCallback();
      callback.setDone();

      if (!mySrcFile.isValid()) return callback;

      if (myLibrary != getLibraryFromOrderEntriesList(orderEntriesContainingFile)) return callback;

      AccessToken accessToken = WriteAction.start();
      try {
        addSourceFile(mySrcFile, myLibrary);
      }
      finally {
        accessToken.finish();
      }

      return callback;
    }
  }

  protected abstract class DownloadSourcesAction implements AttachSourcesAction {
    protected final Project myProject;
    protected final String myUrl;
    protected final String myMessageGroupId;

    public DownloadSourcesAction(Project project, String messageGroupId, String url) {
      myProject = project;
      myUrl = url;
      myMessageGroupId = messageGroupId;
    }

    @Override
    public String getName() {
      return "Download Sources";
    }

    @Override
    public String getBusyText() {
      return "Downloading Sources...";
    }

    protected abstract void storeFile(byte[] content);

    @Override
    public ActionCallback perform(List<LibraryOrderEntry> orderEntriesContainingFile) {
      final ActionCallback callback = new ActionCallback();
      Task task = new Task.Backgroundable(myProject, "Downloading Sources", true) {
        @Override
        public void run(@NotNull final ProgressIndicator indicator) {
          final byte[] bytes;
          try {
            LOG.info("Downloading sources JAR: " + myUrl);
            indicator.checkCanceled();
            bytes = HttpRequests.request(myUrl).readBytes(indicator);
          }
          catch (IOException e) {
            LOG.warn(e);
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                new Notification(myMessageGroupId,
                                 "Downloading failed",
                                 "Failed to download sources: " + myUrl,
                                 NotificationType.ERROR)
                  .notify(getProject());

                callback.setDone();
              }
            });
            return;
          }

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              AccessToken accessToken = WriteAction.start();
              try {
                storeFile(bytes);
              }
              finally {
                accessToken.finish();
                callback.setDone();
              }
            }
          });
        }

        @Override
        public void onCancel() {
          callback.setRejected();
        }
      };

      task.queue();

      return callback;
    }
  }
}
