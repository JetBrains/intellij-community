/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.DocumentRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.UniqueFileNamesProvider;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Parent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author mike
 */
public class StorageUtil {
  private static final Logger LOG = Logger.getInstance(StorageUtil.class);

  private static final boolean DUMP_COMPONENT_STATES = SystemProperties.getBooleanProperty("idea.log.externally.changed.component.states", false);
  @SuppressWarnings("SpellCheckingInspection")
  private static final SimpleDateFormat LOG_DIR_FORMAT = new SimpleDateFormat("yyyyMMdd-HHmmss");
  private static final Pair<byte[], String> NON_EXISTENT_FILE_DATA = Pair.create(null, SystemProperties.getLineSeparator());

  private StorageUtil() { }

  public static void notifyUnknownMacros(@NotNull TrackingPathMacroSubstitutor substitutor,
                                         @NotNull final Project project,
                                         @Nullable String componentName) {
    final LinkedHashSet<String> macros = new LinkedHashSet<String>(substitutor.getUnknownMacros(componentName));
    if (macros.isEmpty()) {
      return;
    }

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        macros.removeAll(getMacrosFromExistingNotifications(project));

        if (!macros.isEmpty()) {
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
                                           ((ProjectEx)project).checkUnknownMacros(true);
                                         }
                                       }, macros).notify(project);
        }
      }
    });
  }

  private static List<String> getMacrosFromExistingNotifications(Project project) {
    List<String> notified = ContainerUtil.newArrayList();
    NotificationsManager manager = NotificationsManager.getNotificationsManager();
    for (final UnknownMacroNotification notification : manager.getNotificationsOfType(UnknownMacroNotification.class, project)) {
      notified.addAll(notification.getMacros());
    }
    return notified;
  }


  public static boolean isEmpty(@Nullable Parent element) {
    if (element == null) {
      return true;
    }
    else if (element instanceof Element) {
      return JDOMUtil.isEmpty((Element)element);
    }
    else {
      Document document = (Document)element;
      return !document.hasRootElement() || JDOMUtil.isEmpty(document.getRootElement());
    }
  }

  /**
   * Due to historical reasons files in ROOT_CONFIG don’t wrapped into document (xml prolog) opposite to files in APP_CONFIG
   */
  @Nullable
  static VirtualFile save(@NotNull File file, @Nullable Parent element, Object requestor, boolean wrapAsDocument, @Nullable VirtualFile cachedVirtualFile) throws StateStorageException {
    if (isEmpty(element)) {
      if (!file.exists()) {
        return null;
      }

      VirtualFile virtualFile = cachedVirtualFile;
      if (virtualFile == null || !virtualFile.isValid()) {
        virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
      }
      if (virtualFile == null) {
        LOG.info("Cannot find virtual file " + file.getAbsolutePath());
        FileUtil.delete(file);
      }
      else {
        AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(DocumentRunnable.IgnoreDocumentRunnable.class);
        try {
          virtualFile.delete(requestor);
        }
        catch (IOException e) {
          throw new StateStorageException(e);
        }
        finally {
          token.finish();
        }
      }
      return null;
    }

    VirtualFile virtualFile = cachedVirtualFile == null || !cachedVirtualFile.isValid() ? null : cachedVirtualFile;
    Parent document = !wrapAsDocument || element instanceof Document ? element : new Document((Element)element);
    try {
      BufferExposingByteArrayOutputStream byteOut;
      if (file.exists()) {
        if (virtualFile == null) {
          virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        }

        Pair<byte[], String> pair = loadFile(virtualFile);
        byteOut = writeToBytes(document, pair.second);
        if (equal(pair.first, byteOut)) {
          return null;
        }
      }
      else {
        FileUtil.createParentDirs(file);
        byteOut = writeToBytes(document, SystemProperties.getLineSeparator());
      }

      // mark this action as modifying the file which daemon analyzer should ignore
      AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(DocumentRunnable.IgnoreDocumentRunnable.class);
      try {
        if (virtualFile == null) {
          virtualFile = getOrCreateVirtualFile(requestor, file);
        }
        OutputStream virtualFileOut = virtualFile.getOutputStream(requestor);
        try {
          byteOut.writeTo(virtualFileOut);
        }
        finally {
          virtualFileOut.close();
        }
        return virtualFile;
      }
      finally {
        token.finish();
      }
    }
    catch (IOException e) {
      throw new StateStorageException(e);
    }
  }

  @NotNull
  private static BufferExposingByteArrayOutputStream writeToBytes(@NotNull Parent element, @NotNull String lineSeparator) throws IOException {
    BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream(512);
    JDOMUtil.writeParent(element, out, lineSeparator);
    return out;
  }

  @NotNull
  static VirtualFile getOrCreateVirtualFile(@Nullable Object requestor, @NotNull File ioFile) throws IOException {
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    if (virtualFile == null) {
      File parentFile = ioFile.getParentFile();
      // need refresh if the directory has just been created
      VirtualFile parentVirtualFile = parentFile == null ? null : LocalFileSystem.getInstance().refreshAndFindFileByIoFile(parentFile);
      if (parentVirtualFile == null) {
        throw new IOException(ProjectBundle.message("project.configuration.save.file.not.found", parentFile == null ? "" : parentFile.getPath()));
      }
      virtualFile = parentVirtualFile.createChildData(requestor, ioFile.getName());
    }
    return virtualFile;
  }

  /**
   * @return pair.first - file contents (null if file does not exist), pair.second - file line separators
   */
  @NotNull
  private static Pair<byte[], String> loadFile(@Nullable final VirtualFile file) throws IOException {
    if (file == null || !file.exists()) {
      return NON_EXISTENT_FILE_DATA;
    }

    byte[] bytes = file.contentsToByteArray();
    String lineSeparator = file.getDetectedLineSeparator();
    if (lineSeparator == null) {
      String fileText = new String(bytes, CharsetToolkit.UTF8);
      final int index = fileText.indexOf('\n');
      lineSeparator = index == -1
                      ? SystemProperties.getLineSeparator()
                      : index - 1 >= 0 ? fileText.charAt(index - 1) == '\r' ? "\r\n" : "\n" : "\n";
    }
    return Pair.create(bytes, lineSeparator);
  }

  public static boolean contentEquals(@NotNull Parent element, @NotNull VirtualFile file) {
    return newContentIfDiffers(element, file) == null;
  }

  @Nullable
  public static BufferExposingByteArrayOutputStream newContentIfDiffers(@NotNull Parent element, @Nullable VirtualFile file) {
    try {
      Pair<byte[], String> pair = loadFile(file);
      BufferExposingByteArrayOutputStream out = writeToBytes(element, pair.second);
      return pair.first != null && equal(pair.first, out) ? null : out;
    }
    catch (IOException e) {
      LOG.debug(e);
      return null;
    }
  }

  public static boolean equal(byte[] a1, @NotNull BufferExposingByteArrayOutputStream out) {
    int length = out.size();
    if (a1.length != length) {
      return false;
    }

    byte[] internalBuffer = out.getInternalBuffer();
    for (int i = 0; i < length; i++) {
      if (a1[i] != internalBuffer[i]) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public static Document loadDocument(final byte[] bytes) {
    try {
      return bytes == null || bytes.length == 0 ? null : JDOMUtil.loadDocument(new ByteArrayInputStream(bytes));
    }
    catch (JDOMException e) {
      return null;
    }
    catch (IOException e) {
      return null;
    }
  }

  @SuppressWarnings("Contract")
  @Nullable
  public static Document loadDocument(@Nullable InputStream stream) {
    if (stream == null) {
      return null;
    }

    try {
      try {
        return JDOMUtil.loadDocument(stream);
      }
      finally {
        stream.close();
      }
    }
    catch (JDOMException e) {
      return null;
    }
    catch (IOException e) {
      return null;
    }
  }

  @NotNull
  public static BufferExposingByteArrayOutputStream elementToBytes(@NotNull Parent element, boolean useSystemLineSeparator) throws IOException {
    return writeToBytes(element, useSystemLineSeparator ? SystemProperties.getLineSeparator() : "\n");
  }

  public static void sendContent(@NotNull StreamProvider provider, @NotNull String fileSpec, @NotNull Parent element, @NotNull RoamingType type, boolean async) {
    if (!provider.isApplicable(fileSpec, type)) {
      return;
    }

    try {
      doSendContent(provider, fileSpec, element, type, async);
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  public static void delete(@NotNull StreamProvider provider, @NotNull String fileSpec, @NotNull RoamingType type) {
    if (provider.isApplicable(fileSpec, type)) {
      provider.delete(fileSpec, type);
    }
  }

  /**
   * You must call {@link StreamProvider#isApplicable(String, com.intellij.openapi.components.RoamingType)} before
   */
  public static void doSendContent(@NotNull StreamProvider provider, @NotNull String fileSpec, @NotNull Parent element, @NotNull RoamingType type, boolean async) throws IOException {
    // we should use standard line-separator (\n) - stream provider can share file content on any OS
    BufferExposingByteArrayOutputStream content = elementToBytes(element, false);
    provider.saveContent(fileSpec, content.getInternalBuffer(), content.size(), type, async);
  }

  public static void logStateDiffInfo(Set<Pair<VirtualFile, StateStorage>> changedFiles, Set<String> componentNames) {
    if (componentNames.isEmpty() || !(DUMP_COMPONENT_STATES || ApplicationManager.getApplication().isInternal())) {
      return;
    }

    try {
      File logDirectory = createLogDirectory();
      if (!logDirectory.mkdirs()) {
        throw new IOException("Cannot create " + logDirectory);
      }

      for (Pair<VirtualFile, StateStorage> pair : changedFiles) {
        File file = new File(pair.first.getPath());
        StateStorage storage = pair.second;

        if (storage instanceof XmlElementStorage) {
          Element state = ((XmlElementStorage)storage).logComponents();
          if (state != null) {
            JDOMUtil.writeParent(state, new File(logDirectory, "prev_" + file.getName()), "\n");
          }
        }

        if (file.exists()) {
          File logFile = new File(logDirectory, "new_" + file.getName());
          FileUtil.copy(file, logFile);
        }
      }

      File logFile = new File(logDirectory, "components.txt");
      FileUtil.writeToFile(logFile, componentNames.toString() + "\n");
    }
    catch (Throwable e) {
      LOG.info(e);
    }
  }

  private static File createLogDirectory() {
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

    String name = "state-" + LOG_DIR_FORMAT.format(new Date()) + "-" + ApplicationInfo.getInstance().getBuild().asString();
    return new File(statesDir, namesProvider.suggestName(name));
  }

  public static boolean isProjectOrModuleFile(@NotNull String fileSpec) {
    return StoragePathMacros.PROJECT_FILE.equals(fileSpec) || fileSpec.startsWith(StoragePathMacros.PROJECT_CONFIG_DIR) || fileSpec.equals("$MODULE_FILE$");
  }
}
