/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.StreamProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import com.intellij.util.SystemProperties;
import com.intellij.util.UniqueFileNamesProvider;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.fs.FileSystem;
import com.intellij.util.io.fs.IFile;
import org.jdom.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Set;

/**
 * @author mike
 */
public class StorageUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.StorageUtil");
  private static final boolean ourDumpChangedComponentStates = "true".equals(System.getProperty("log.externally.changed.component.states"));

  private StorageUtil() {
  }

  public static void notifyUnknownMacros(@NotNull final TrackingPathMacroSubstitutor substitutor, @NotNull final Project project, @Nullable final String componentName) {
    Collection<String> macros = substitutor.getUnknownMacros(componentName);
    if (!macros.isEmpty()) {
      final UnknownMacroNotification[] notifications =
        NotificationsManager.getNotificationsManager().getNotificationsOfType(UnknownMacroNotification.class, project);
      for (final UnknownMacroNotification notification : notifications) {
        macros = ContainerUtil.subtract(macros, notification.getMacros());
      }

      if (!macros.isEmpty()) {
        Notifications.Bus.notify(new UnknownMacroNotification("Load Error", "Load error: undefined path variables!",
                                                              String.format("<p><i>%s</i> %s undefined. <a href=\"define\">Fix it</a>.</p>",
                                                                            StringUtil.join(macros, ", "),
                                                                            macros.size() == 1 ? "is" : "are"), NotificationType.ERROR,
                                                              new NotificationListener() {
                                                                public void hyperlinkUpdate(@NotNull Notification notification,
                                                                                            @NotNull HyperlinkEvent event) {
                                                                  ((ProjectEx)project).checkUnknownMacros(true);
                                                                }
                                                              }, macros), NotificationDisplayType.STICKY_BALLOON, project);
      }
    }
  }

  static void save(final IFile file, final Parent element, final Object requestor) throws StateStorage.StateStorageException {
    final String filePath = file.getCanonicalPath();
    try {
      final Ref<IOException> refIOException = Ref.create(null);

      final Pair<String, String> pair = loadFile(file);
      final byte[] text = JDOMUtil.writeParent(element, pair.second).getBytes(CharsetToolkit.UTF8);
      if (file.exists()) {
        if (new String(text).equals(pair.first)) return;
        IFile backupFile = deleteBackup(filePath);
        file.renameTo(backupFile);
      }

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          if (!file.exists()) {
            file.createParentDirs();
          }

          try {
            final VirtualFile virtualFile = getOrCreateVirtualFile(requestor, file);

            OutputStream outputStream = null;
            try {
              outputStream = virtualFile.getOutputStream(requestor);
              outputStream.write(text);
              outputStream.flush();
            }
            finally {
              if (outputStream != null) outputStream.close();
            }
          }
          catch (IOException e) {
            refIOException.set(e);
          }

          deleteBackup(filePath);
        }
      });
      if (refIOException.get() != null) {
        throw new StateStorage.StateStorageException(refIOException.get());
      }
    }
    catch (IOException e) {
      throw new StateStorage.StateStorageException(e);
    }
  }

  static IFile deleteBackup(final String path) {
    IFile backupFile = FileSystem.FILE_SYSTEM.createFile(path + "~");
    if (backupFile.exists()) {
      backupFile.delete();
    }
    return backupFile;
  }

  static VirtualFile getOrCreateVirtualFile(final Object requestor, final IFile ioFile) throws IOException {
    VirtualFile vFile = getVirtualFile(ioFile);

    if (vFile == null) {
      vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    }

    if (vFile == null) {
      final IFile parentFile = ioFile.getParentFile();
      final VirtualFile parentVFile =
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(parentFile); // need refresh if the directory has just been created
      if (parentVFile == null) {
        throw new IOException(ProjectBundle.message("project.configuration.save.file.not.found", parentFile.getPath()));
      }
      vFile = parentVFile.createChildData(requestor, ioFile.getName());
    }

    return vFile;
  }

  @Nullable
  static VirtualFile getVirtualFile(final IFile ioFile) {
    return LocalFileSystem.getInstance().findFileByIoFile(ioFile);
  }

  public static byte[] printDocument(final Document document) throws StateStorage.StateStorageException {
    try {
      return printDocumentToString(document).getBytes(CharsetToolkit.UTF8);
    }
    catch (IOException e) {
      throw new StateStorage.StateStorageException(e);
    }
  }

  /**
   * @return pair.first - file contents (null if file does not exist), pair.second - file line separators
   */
  public static Pair<String, String> loadFile(@NotNull final IFile file) throws IOException {
    if (!file.exists()) return Pair.create(null, SystemProperties.getLineSeparator());

    String fileText = new String(file.loadBytes(), CharsetToolkit.UTF8);
    if (!Registry.is("storage.preserve.line.separators")) return Pair.create(fileText, SystemProperties.getLineSeparator());

    final int ndx = fileText.indexOf('\n');
    return Pair.create(fileText, ndx == -1
                                 ? SystemProperties.getLineSeparator()
                                 : (ndx - 1 >=0 ? (fileText.charAt(ndx - 1) == '\r' ? "\r\n" : "\n") : "\n"));
  }

  public static boolean contentEquals(@NotNull final Document document, @NotNull final IFile file) {
    try {
      final Pair<String, String> pair = loadFile(file);
      return pair.first == null ? false : pair.first.equals(printDocumentToString(document, pair.second));
    }
    catch (IOException e) {
      LOG.debug(e);
      return false;
    }
  }

  public static boolean contentEquals(@NotNull final Element element, @NotNull final IFile file) {
    try {
      final Pair<String, String> pair = loadFile(file);
      return pair.first == null ? false : pair.first.equals(printElement(element, pair.second));
    }
    catch (IOException e) {
      LOG.debug(e);
      return false;
    }
  }

  public static String printDocumentToString(final Document document, final String lineSeparator) {
    return JDOMUtil.writeDocument(document, lineSeparator);
  }

  @Deprecated
  public static String printDocumentToString(final Document document) {
    return printDocumentToString(document, SystemProperties.getLineSeparator());
  }

  static String printElement(final Element element, final String lineSeparator) throws StateStorage.StateStorageException {
    return JDOMUtil.writeElement(element, lineSeparator);
  }

  @NotNull
  public static Set<String> getMacroNames(@NotNull final Element e) {
    return PathMacrosCollector.getMacroNames(e, new NotNullFunction<Object, Boolean>() {
      @NotNull
      public Boolean fun(Object o) {
        if (o instanceof Attribute) {
          final Attribute attribute = (Attribute)o;
          final Element parent = attribute.getParent();
          final String parentName = parent.getName();
          if (("value".equals(attribute.getName()) || "name".equals(attribute.getName())) && "env".equals(parentName)) {
            return false; // do not proceed environment variables from run configurations
          }

          // do not proceed macros in searchConfigurations (structural search)
          if ("replaceConfiguration".equals(parentName) || "searchConfiguration".equals(parentName)) return false;
        }

        return true;
      }
    }, new NotNullFunction<Object, Boolean>() {
      @NotNull
      public Boolean fun(Object o) {
        if (o instanceof Attribute) {
          // process run configuration's options recursively
          final Element parent = ((Attribute)o).getParent();
          if (parent != null && "option".equals(parent.getName())) {
            final Element grandParent = parent.getParentElement();
            return grandParent != null && "configuration".equals(grandParent.getName());
          }
        }

        return false;
      }
    });
  }

  @Nullable
  public static Document loadDocument(final byte[] bytes) {
    try {
      return (bytes == null || bytes.length == 0) ? null : JDOMUtil.loadDocument(new ByteArrayInputStream(bytes));
    }
    catch (JDOMException e) {
      return null;
    }
    catch (IOException e) {
      return null;
    }
  }

  @Nullable
  public static Document loadDocument(final InputStream stream) {
    if (stream == null) return null;

    try {
      return JDOMUtil.loadDocument(stream);
    }
    catch (JDOMException e) {
      return null;
    }
    catch (IOException e) {
      return null;
    }
    finally {
      try {
        stream.close();
      }
      catch (IOException e) {
        //ignore
      }
    }
  }

  public static void sendContent(final StreamProvider streamProvider, final String fileSpec, final Document copy, final RoamingType roamingType, boolean async)
      throws IOException {
    byte[] content = printDocument(copy);
    ByteArrayInputStream in = new ByteArrayInputStream(content);
    try {
      if (streamProvider.isEnabled()) {
        streamProvider.saveContent(fileSpec, in, content.length, roamingType, async);
      }
    }
    finally {
      in.close();
    }

  }

  public static void logStateDiffInfo(Set<Pair<VirtualFile, StateStorage>> changedFiles, Set<String> componentNames) throws IOException {

    if (!ApplicationManagerEx.getApplicationEx().isInternal() && !ourDumpChangedComponentStates) return;

    try {
      File logDirectory = createLogDirectory();

      logDirectory.mkdirs();

      for (String componentName : componentNames) {
        for (Pair<VirtualFile, StateStorage> pair : changedFiles) {
          StateStorage storage = pair.second;
          if ((storage instanceof XmlElementStorage)) {
            Element state = ((XmlElementStorage)storage).getState(componentName);
            if (state != null) {
              File logFile = new File(logDirectory, "prev_" + componentName + ".xml");
              FileUtil.writeToFile(logFile, JDOMUtil.writeElement(state, "\n").getBytes());
            }
          }
        }

      }

      for (Pair<VirtualFile, StateStorage> changedFile : changedFiles) {
        File logFile = new File(logDirectory, "new_" + changedFile.first.getName());

        FileUtil.copy(new File(changedFile.first.getPath()), logFile);
      }
    }
    catch (Throwable e) {
      LOG.info(e);
    }
  }

  static File createLogDirectory() {
    UniqueFileNamesProvider namesProvider = new UniqueFileNamesProvider();

    File statesDir = new File(PathManager.getSystemPath(), "log/componentStates");
    File[] children = statesDir.listFiles();
    if (children != null) {
      if (children.length > 10) {
        File childToDelete = null;

        for (File child : children) {
          if (childToDelete == null || childToDelete.lastModified() > child.lastModified()) {
            childToDelete = child;
          }
        }

        if (childToDelete != null) {
          FileUtil.delete(childToDelete);
        }
      }


      for (File child : children) {
        namesProvider.reserveFileName(child.getName());
      }
    }

    return new File(statesDir, namesProvider.suggestName("state-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())
                        + "-" + ApplicationInfo.getInstance().getBuild().asString()));
  }
}
