package org.jetbrains.plugins.groovy.ivy;

import com.intellij.codeInsight.AttachSourcesProvider;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class IvyAttachSourceProvider implements AttachSourcesProvider {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.ivy.IvyAttachSourceProvider");

  @Nullable
  private static VirtualFile getJarByPsiFile(PsiFile psiFile) {
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return null;

    VirtualFile jar = JarFileSystem.getInstance().getVirtualFileForJar(psiFile.getVirtualFile());

    if (jar == null || !jar.getName().endsWith(".jar")) return null;

    return jar;
  }

  @Nullable
  private static Library getLibraryFromOrderEntriesList(List<LibraryOrderEntry> orderEntries) {
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

  private static void addSourceFile(@Nullable VirtualFile jarRoot, @NotNull Library library) {
    if (jarRoot != null) {
      Library.ModifiableModel model = library.getModifiableModel();
      model.addRoot(jarRoot, OrderRootType.SOURCES);
      model.commit();
    }
  }

  @Nullable
  private static String extractUrl(PropertiesFile properties, String artifactName) {
    String prefix = "artifact:" + artifactName + "#source#jar#";

    for (Property property : properties.getProperties()) {
      String key = property.getUnescapedKey();
      if (key != null && key.startsWith(prefix) && key.endsWith(".location")) {
        return property.getUnescapedValue();
      }
    }

    return null;
  }

  @Override
  public Collection<AttachSourcesAction> getActions(List<LibraryOrderEntry> orderEntries, final PsiFile psiFile) {
    VirtualFile jar = getJarByPsiFile(psiFile);
    if (jar == null) return Collections.emptyList();

    VirtualFile jarsDir = jar.getParent();
    if (jarsDir == null || !jarsDir.getName().equals("jars")) return Collections.emptyList();

    String jarNameWithoutExt = jar.getNameWithoutExtension();

    final VirtualFile artifactDir = jarsDir.getParent();
    if (artifactDir == null) return Collections.emptyList();

    String artifactName = artifactDir.getName();

    if (!jarNameWithoutExt.startsWith(artifactName)
        || !jarNameWithoutExt.substring(artifactName.length()).startsWith("-")) {
      return Collections.emptyList();
    }

    String version = jarNameWithoutExt.substring(artifactName.length() + 1);

    VirtualFile propertiesFile = artifactDir.findChild("ivydata-" + version + ".properties");
    if (propertiesFile == null) return Collections.emptyList();

    final Library library = getLibraryFromOrderEntriesList(orderEntries);
    if (library == null) return null;

    final String sourceFileName = artifactName + '-' + version + "-sources.jar";

    final VirtualFile sources = artifactDir.findChild("sources");
    if (sources != null) {
      VirtualFile srcFile = sources.findChild(sourceFileName);
      if (srcFile != null) {
        // File already downloaded.
        VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(srcFile);
        if (jarRoot == null || ArrayUtil.contains(jarRoot, library.getFiles(OrderRootType.SOURCES))) {
          return Collections.emptyList(); // Sources already attached.
        }

        return Collections.<AttachSourcesAction>singleton(new AttachExistingSourceAction(jarRoot, library));
      }
    }

    PsiFile propertiesFileFile = psiFile.getManager().findFile(propertiesFile);
    if (!(propertiesFileFile instanceof PropertiesFile)) return Collections.emptyList();

    final String url = extractUrl((PropertiesFile)propertiesFileFile, artifactName);
    if (StringUtil.isEmptyOrSpaces(url)) return Collections.emptyList();

    return Collections.<AttachSourcesAction>singleton(new AttachSourcesAction() {
      @Override
      public String getName() {
        return "Download Sources";
      }

      @Override
      public String getBusyText() {
        return "Downloading Sources...";
      }

      @Override
      public ActionCallback perform(List<LibraryOrderEntry> orderEntriesContainingFile) {

        final ActionCallback callback = new ActionCallback();

        Task task = new Task.Backgroundable(psiFile.getProject(), "Downloading sources...", true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            final ByteArrayOutputStream out;

            try {
              LOG.info("Downloading sources jar: " + url);

              HttpConfigurable.getInstance().prepareURL(url);

              indicator.checkCanceled();

              HttpURLConnection urlConnection = (HttpURLConnection)new URL(url).openConnection();

              int contentLength = urlConnection.getContentLength();

              out = new ByteArrayOutputStream(contentLength > 0 ? contentLength : 100 * 1024);

              InputStream in = urlConnection.getInputStream();

              try {
                NetUtils.copyStreamContent(indicator, in, out, contentLength);
              }
              finally {
                in.close();
              }
            }
            catch (IOException e) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  new Notification("Downloading Ivy Sources",
                                   "Downloading failed",
                                   "Failed to download sources: " + url,
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
                  VirtualFile existingSourcesFolder = sources;
                  if (existingSourcesFolder == null) {
                    existingSourcesFolder = artifactDir.createChildDirectory(this, "sources");
                  }

                  VirtualFile srcFile = existingSourcesFolder.createChildData(this, sourceFileName);
                  srcFile.setBinaryContent(out.toByteArray());

                  addSourceFile(JarFileSystem.getInstance().getJarRootForLocalFile(srcFile), library);
                }
                catch (IOException e) {
                  new Notification("Downloading Ivy Sources",
                                   "IO Error",
                                   "Failed to save " + artifactDir.getPath() + "/sources/" + sourceFileName,
                                   NotificationType.ERROR)
                    .notify(getProject());
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
    });
  }

  private static class AttachExistingSourceAction implements AttachSourcesAction {

    private final VirtualFile mySrcFile;
    private final Library myLibrary;

    private AttachExistingSourceAction(VirtualFile srcFile, Library library) {
      mySrcFile = srcFile;
      myLibrary = library;
    }

    @Override
    public String getName() {
      return "Attache sources from Ivy repository";
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
}
