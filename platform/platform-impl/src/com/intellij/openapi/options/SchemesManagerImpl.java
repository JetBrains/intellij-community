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
package com.intellij.openapi.options;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.StateStorageException;
import com.intellij.openapi.components.impl.stores.DirectoryBasedStorage;
import com.intellij.openapi.components.impl.stores.DirectoryStorageData;
import com.intellij.openapi.components.impl.stores.StorageUtil;
import com.intellij.openapi.components.impl.stores.StreamProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.DocumentRunnable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.tracker.VirtualFileTracker;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.text.UniqueNameGenerator;
import gnu.trove.THashSet;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Parent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class SchemesManagerImpl<T extends Scheme, E extends ExternalizableScheme> extends AbstractSchemesManager<T, E> {
  private static final Logger LOG = Logger.getInstance(SchemesManagerFactoryImpl.class);

  private static final String NAME = "name";

  private final String myFileSpec;
  private final SchemeProcessor<E> myProcessor;
  private final RoamingType myRoamingType;

  private final StreamProvider myProvider;
  private final File myIoDir;
  private VirtualFile myDir;

  private String mySchemeExtension = DirectoryStorageData.DEFAULT_EXT;
  private boolean myUpgradeExtension;

  private final Set<String> myFilesToDelete = new THashSet<String>();

  public SchemesManagerImpl(@NotNull String fileSpec,
                            @NotNull SchemeProcessor<E> processor,
                            @NotNull RoamingType roamingType,
                            @Nullable StreamProvider provider,
                            @NotNull File baseDir) {
    myFileSpec = fileSpec;
    myProcessor = processor;
    myRoamingType = roamingType;
    myProvider = provider;
    myIoDir = baseDir;
    if (processor instanceof SchemeExtensionProvider) {
      mySchemeExtension = ((SchemeExtensionProvider)processor).getSchemeExtension();
      myUpgradeExtension = ((SchemeExtensionProvider)processor).isUpgradeNeeded();
    }

    VirtualFileTracker virtualFileTracker = ServiceManager.getService(VirtualFileTracker.class);
    if (virtualFileTracker != null) {
      final String baseDirPath = myIoDir.getAbsolutePath().replace(File.separatorChar, '/');
      virtualFileTracker.addTracker(LocalFileSystem.PROTOCOL_PREFIX + baseDirPath, new VirtualFileAdapter() {
        @Override
        public void contentsChanged(@NotNull VirtualFileEvent event) {
          if (event.getRequestor() != null || !isMy(event)) {
            return;
          }

          E scheme = findSchemeFor(event.getFile().getName());
          T oldCurrentScheme = null;
          if (scheme != null) {
            oldCurrentScheme = getCurrentScheme();
            //noinspection unchecked
            removeScheme((T)scheme);
            myProcessor.onSchemeDeleted(scheme);
          }

          E readScheme = readSchemeFromFile(event.getFile(), true, Collections.<String, E>emptyMap());
          if (readScheme != null) {
            myProcessor.initScheme(readScheme);
            myProcessor.onSchemeAdded(readScheme);

            T newCurrentScheme = getCurrentScheme();
            if (oldCurrentScheme != null && newCurrentScheme == null) {
              setCurrentSchemeName(readScheme.getName());
              newCurrentScheme = getCurrentScheme();
            }

            if (oldCurrentScheme != newCurrentScheme) {
              myProcessor.onCurrentSchemeChanged(oldCurrentScheme);
            }
          }
        }

        @Override
        public void fileCreated(@NotNull VirtualFileEvent event) {
          if (event.getRequestor() == null && isMy(event)) {
            E readScheme = readSchemeFromFile(event.getFile(), true, Collections.<String, E>emptyMap());
            if (readScheme != null) {
              myProcessor.initScheme(readScheme);
              myProcessor.onSchemeAdded(readScheme);
            }
          }
        }

        @Override
        public void fileDeleted(@NotNull VirtualFileEvent event) {
          if (event.getRequestor() == null && isMy(event)) {
            E scheme = findSchemeFor(event.getFile().getName());
            T oldCurrentScheme = null;
            if (scheme != null) {
              oldCurrentScheme = getCurrentScheme();
              //noinspection unchecked
              removeScheme((T)scheme);
              myProcessor.onSchemeDeleted(scheme);
            }

            T newCurrentScheme = getCurrentScheme();
            if (oldCurrentScheme != null && newCurrentScheme == null) {
              if (!mySchemes.isEmpty()) {
                setCurrentSchemeName(mySchemes.get(0).getName());
                newCurrentScheme = getCurrentScheme();
              }
            }

            if (oldCurrentScheme != newCurrentScheme) {
              myProcessor.onCurrentSchemeChanged(oldCurrentScheme);
            }
          }
        }
      }, false, ApplicationManager.getApplication());
    }
  }

  private boolean isMy(@NotNull VirtualFileEvent event) {
    return StringUtilRt.endsWithIgnoreCase(event.getFile().getNameSequence(), mySchemeExtension);
  }

  @Override
  @NotNull
  public Collection<E> loadSchemes() {
    Map<String, E> result = new LinkedHashMap<String, E>();
    if (myProvider != null && myProvider.isEnabled()) {
      readSchemesFromProviders(result);
    }
    else {
      VirtualFile dir = getVirtualDir();
      VirtualFile[] files = dir == null ? null : dir.getChildren();
      if (files != null) {
        for (VirtualFile file : files) {
          E scheme = readSchemeFromFile(file, false, Collections.<String, E>emptyMap());
          if (scheme != null) {
            result.put(scheme.getName(), scheme);
          }
        }
      }
    }

    Collection<E> list = result.values();
    for (E scheme : list) {
      myProcessor.initScheme(scheme);
      checkCurrentScheme(scheme);
    }
    return list;
  }

  private E findSchemeFor(@NotNull String ioFileName) {
    for (T scheme : mySchemes) {
      if (scheme instanceof ExternalizableScheme) {
        if (ioFileName.equals(((ExternalizableScheme)scheme).getExternalInfo().getCurrentFileName() + mySchemeExtension)) {
          //noinspection CastConflictsWithInstanceof,unchecked
          return (E)scheme;
        }
      }
    }
    return null;
  }

  @Nullable
  private static Element loadElementOrNull(@Nullable InputStream stream) {
    try {
      return JDOMUtil.load(stream);
    }
    catch (JDOMException e) {
      LOG.warn(e);
      return null;
    }
    catch (IOException e) {
      LOG.warn(e);
      return null;
    }
  }

  private void readSchemesFromProviders(@NotNull Map<String, E> result) {
    assert myProvider != null;
    for (String subPath : myProvider.listSubFiles(myFileSpec, myRoamingType)) {
      try {
        Element element = loadElementOrNull(myProvider.loadContent(getFileFullPath(subPath), myRoamingType));
        if (element == null) {
          return;
        }

        E scheme = readScheme(element, Collections.<String, E>emptyMap());
        boolean fileRenamed = false;
        assert scheme != null;
        T existing = findSchemeByName(scheme.getName());
        if (existing instanceof ExternalizableScheme) {
          String currentFileName = ((ExternalizableScheme)existing).getExternalInfo().getCurrentFileName();
          if (currentFileName != null && !currentFileName.equals(subPath)) {
            deleteServerFile(subPath);
            subPath = currentFileName;
            fileRenamed = true;
          }
        }
        String fileName = checkFileNameIsFree(subPath, scheme.getName());
        if (!fileRenamed && !fileName.equals(subPath)) {
          deleteServerFile(subPath);
        }

        loadScheme(scheme, false, fileName);
        scheme.getExternalInfo().markRemote();
        result.put(scheme.getName(), scheme);
      }
      catch (Exception e) {
        LOG.info("Cannot load data from stream provider: " + e.getMessage());
      }
    }
  }

  @NotNull
  private String checkFileNameIsFree(@NotNull String subPath, @NotNull String schemeName) {
    for (Scheme scheme : mySchemes) {
      if (scheme instanceof ExternalizableScheme) {
        String name = ((ExternalizableScheme)scheme).getExternalInfo().getCurrentFileName();
        if (name != null &&
            !schemeName.equals(scheme.getName()) &&
            subPath.length() == (name.length() + mySchemeExtension.length()) &&
            subPath.startsWith(name) &&
            subPath.endsWith(mySchemeExtension)) {
          return UniqueNameGenerator.generateUniqueName(FileUtil.sanitizeName(schemeName), collectAllFileNames());
        }
      }
    }
    return subPath;
  }

  @NotNull
  private Collection<String> collectAllFileNames() {
    Set<String> result = new THashSet<String>();
    for (T scheme : mySchemes) {
      if (scheme instanceof ExternalizableScheme) {
        ExternalInfo externalInfo = ((ExternalizableScheme)scheme).getExternalInfo();
        if (externalInfo.getCurrentFileName() != null) {
          result.add(externalInfo.getCurrentFileName());
        }
      }
    }
    return result;
  }

  private void loadScheme(@NotNull E scheme, boolean forceAdd, @NotNull CharSequence fileName) {
    String fileNameWithoutExtension = createFileName(fileName);
    if (!forceAdd && myFilesToDelete.contains(fileNameWithoutExtension)) {
      return;
    }

    T existing = findSchemeByName(scheme.getName());
    if (existing != null) {
      if (!Comparing.equal(existing.getClass(), scheme.getClass())) {
        LOG.warn("'" + scheme.getName() + "' " + existing.getClass().getSimpleName() + " replaced with " + scheme.getClass().getSimpleName());
      }

      mySchemes.remove(existing);

      if (isExternalizable(existing)) {
        //noinspection unchecked
        myProcessor.onSchemeDeleted((E)existing);
      }
    }

    //noinspection unchecked
    addNewScheme((T)scheme, true);
    scheme.getExternalInfo().setPreviouslySavedName(scheme.getName());
    scheme.getExternalInfo().setCurrentFileName(fileNameWithoutExtension);
  }

  private boolean canRead(@NotNull VirtualFile file) {
    if (!file.isDirectory()) {
      if (myUpgradeExtension && !DirectoryStorageData.DEFAULT_EXT.equals(mySchemeExtension) && DirectoryStorageData.isStorageFile(file)) {
        return myDir.findChild(file.getNameSequence() + mySchemeExtension) == null;
      }
      else if (StringUtilRt.endsWithIgnoreCase(file.getNameSequence(), mySchemeExtension)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private E readSchemeFromFile(@NotNull final VirtualFile file, boolean forceAdd, @NotNull Map<String, E> filter) {
    if (!canRead(file)) {
      return null;
    }

    try {
      Element element;
      try {
        element = JDOMUtil.load(file.getInputStream());
      }
      catch (JDOMException e) {
        try {
          File initialIOFile = new File(myIoDir, file.getName());
          if (initialIOFile.isFile()) {
            FileUtil.copy(initialIOFile, new File(myIoDir, file.getName() + ".copy"));
          }
        }
        catch (IOException e1) {
          LOG.info(e1);
        }
        LOG.info("Error reading file " + file.getPath() + ": " + e.getMessage() );
        throw e;
      }

      E scheme = readScheme(element, filter);
      if (scheme != null) {
        loadScheme(scheme, forceAdd, file.getNameSequence());
        return scheme;
      }
    }
    catch (final Exception e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                          String msg = "Cannot read scheme " + file.getName() + "  from '" + myFileSpec + "': " + e.getLocalizedMessage();
                                                          LOG.info(msg, e);
                                                          Messages.showErrorDialog(msg, "Load Settings");
                                                        }
                                                      }
      );
    }

    return null;
  }

  @Nullable
  private E readScheme(@NotNull Element element, @NotNull Map<String, E> filter) throws InvalidDataException, IOException, JDOMException {
    if (element.getName().equals("shared-scheme")) {
      String schemeName = element.getAttributeValue(NAME);
      if (filter.containsKey(schemeName)) {
        return null;
      }

      String schemePath = element.getAttributeValue("original-scheme-path");
      Element sharedElement = myProvider != null && myProvider.isEnabled() ? loadElementOrNull(myProvider.loadContent(schemePath, myRoamingType)) : null;
      if (sharedElement == null) {
        Element localCopyElement = element.getChild("scheme-local-copy");
        E scheme = localCopyElement == null ? null : doReadScheme(localCopyElement.getChildren().get(0));
        return scheme == null || filter.containsKey(scheme.getName()) ? null : scheme;
      }
      else {
        E result = readScheme(sharedElement, Collections.<String, E>emptyMap());
        if (result != null) {
          renameScheme(result, schemeName);
        }
        return result;
      }
    }
    else if (element.getName().equals("shared-scheme-original")) {
      E scheme = doReadScheme(element.getChildren().get(0));
      if (scheme == null || filter.containsKey(scheme.getName())) {
        return null;
      }
      renameScheme(scheme, element.getAttributeValue(NAME));
      return scheme;
    }
    else {
      E scheme = doReadScheme(element);
      return scheme == null || filter.containsKey(scheme.getName()) ? null : scheme;
    }
  }

  @Nullable
  private E doReadScheme(Element element) throws InvalidDataException, IOException, JDOMException {
    E scheme;
    if (myProcessor instanceof BaseSchemeProcessor) {
      scheme = ((BaseSchemeProcessor<E>)myProcessor).readScheme(element);
    }
    else {
      //noinspection deprecation
      scheme = myProcessor.readScheme(new Document((Element)element.detach()));
    }
    if (scheme != null) {
      scheme.getExternalInfo().setHash(JDOMUtil.getTreeHash(element));
    }
    return scheme;
  }

  @NotNull
  private String createFileName(@NotNull CharSequence fileName) {
    if (StringUtilRt.endsWithIgnoreCase(fileName, mySchemeExtension)) {
      fileName = fileName.subSequence(0, fileName.length() - mySchemeExtension.length());
    }
    else if (StringUtilRt.endsWithIgnoreCase(fileName, DirectoryStorageData.DEFAULT_EXT)) {
      fileName = fileName.subSequence(0, fileName.length() - DirectoryStorageData.DEFAULT_EXT.length());
    }
    return fileName.toString();
  }

  public void updateConfigFilesFromStreamProviders() {
    // todo
  }

  private String getFileFullPath(@NotNull String subPath) {
    return myFileSpec + '/' + subPath;
  }

  @Override
  public boolean isImportAvailable() {
    return false;
  }

  @Override
  public void save() {
    boolean hasSchemes = false;
    UniqueNameGenerator nameGenerator = new UniqueNameGenerator();
    for (T scheme : mySchemes) {
      //noinspection CastConflictsWithInstanceof,unchecked
      if (scheme instanceof ExternalizableScheme && myProcessor.shouldBeSaved((E)scheme)) {
        hasSchemes = true;
        ExternalizableScheme eScheme = (ExternalizableScheme)scheme;
        String fileName = eScheme.getExternalInfo().getCurrentFileName();
        if (fileName != null && !isRenamed(eScheme)) {
          nameGenerator.addExistingName(fileName);
        }
      }
    }

    VirtualFile dir = getVirtualDir();
    if (!hasSchemes) {
      myFilesToDelete.clear();
      if (dir != null && dir.exists()) {
        try {
          StorageUtil.deleteFile(this, dir);
        }
        catch (IOException e) {
          throw new StateStorageException(e);
        }
      }
      return;
    }

    for (final T scheme : mySchemes) {
      if (!isExternalizable(scheme)) {
        continue;
      }

      @SuppressWarnings("unchecked")
      E eScheme = (E)scheme;
      if (myProcessor.shouldBeSaved(eScheme)) {
        try {
          saveScheme(eScheme, nameGenerator);
        }
        catch (final Exception e) {
          Application app = ApplicationManager.getApplication();
          if (app.isUnitTestMode() || app.isCommandLine()) {
            LOG.error("Cannot write scheme " + scheme.getName() + " in '" + myFileSpec + "': " + e.getLocalizedMessage(), e);
          }
          else {
            app.invokeLater(new Runnable() {
              @Override
              public void run() {
                Messages.showErrorDialog("Cannot save scheme '" + scheme.getName() + ": " + e.getMessage(), "Save Settings");
              }
            });
          }
        }
      }
    }

    deleteFiles(dir);
  }

  private void saveScheme(@NotNull E scheme, @NotNull UniqueNameGenerator nameGenerator) throws WriteExternalException, IOException {
    ExternalInfo externalInfo = scheme.getExternalInfo();
    String currentFileNameWithoutExtension = externalInfo.getCurrentFileName();
    Parent parent = myProcessor.writeScheme(scheme);
    Element element = parent == null || parent instanceof Element ? (Element)parent : ((Document)parent).detachRootElement();
    if (JDOMUtil.isEmpty(element)) {
      ContainerUtilRt.addIfNotNull(myFilesToDelete, currentFileNameWithoutExtension);
      return;
    }

    String fileNameWithoutExtension = currentFileNameWithoutExtension;
    if (fileNameWithoutExtension == null || isRenamed(scheme)) {
      fileNameWithoutExtension = nameGenerator.generateUniqueName(FileUtil.sanitizeName(scheme.getName()));
    }
    String fileName = fileNameWithoutExtension + mySchemeExtension;

    int newHash = JDOMUtil.getTreeHash(element);
    if (currentFileNameWithoutExtension == fileNameWithoutExtension && newHash == externalInfo.getHash()) {
      return;
    }

    // file will be overwritten, so, we don't need to delete it
    myFilesToDelete.remove(fileNameWithoutExtension);

    // stream provider always use LF separator
    BufferExposingByteArrayOutputStream byteOut = StorageUtil.writeToBytes(element, "\n");

    // if another new scheme uses old name of this scheme, so, we must not delete it (as part of rename operation)
    boolean renamed = currentFileNameWithoutExtension != null && fileNameWithoutExtension != currentFileNameWithoutExtension && nameGenerator.value(currentFileNameWithoutExtension);
    if (!externalInfo.isRemote()) {
      VirtualFile file = null;
      if (renamed) {
        file = myDir.findChild(currentFileNameWithoutExtension + mySchemeExtension);
        if (file != null) {
          AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(DocumentRunnable.IgnoreDocumentRunnable.class);
          try {
            file.rename(this, fileName);
          }
          finally {
            token.finish();
          }
        }
      }

      if (file == null) {
        if (myDir == null || !myDir.isValid()) {
          myDir = DirectoryBasedStorage.createDir(myIoDir, this);
        }
        file = DirectoryBasedStorage.getFile(fileName, myDir, this);
      }

      OutputStream out = file.getOutputStream(this);
      try {
        byteOut.writeTo(out);
      }
      finally {
        out.close();
      }
    }
    else if (renamed) {
      myFilesToDelete.add(currentFileNameWithoutExtension);
    }

    externalInfo.setHash(newHash);
    externalInfo.setPreviouslySavedName(scheme.getName());
    externalInfo.setCurrentFileName(createFileName(fileName));

    if (myProvider != null && myProvider.isEnabled()) {
      String fileSpec = getFileFullPath(fileName);
      if (myProvider.isApplicable(fileSpec, myRoamingType)) {
        myProvider.saveContent(fileSpec, byteOut.getInternalBuffer(), byteOut.size(), myRoamingType, true);
      }
    }
  }

  private static boolean isRenamed(@NotNull ExternalizableScheme scheme) {
    return !scheme.getName().equals(scheme.getExternalInfo().getPreviouslySavedName());
  }

  private void deleteFiles(@Nullable VirtualFile dir) {
    if (myFilesToDelete.isEmpty()) {
      return;
    }

    if (myProvider != null && myProvider.isEnabled()) {
      for (String nameWithoutExtension : myFilesToDelete) {
        deleteServerFile(nameWithoutExtension + mySchemeExtension);
        if (!DirectoryStorageData.DEFAULT_EXT.equals(mySchemeExtension)) {
          deleteServerFile(nameWithoutExtension + DirectoryStorageData.DEFAULT_EXT);
        }
      }
    }

    if (dir != null) {
      AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(DocumentRunnable.IgnoreDocumentRunnable.class);
      try {
        for (VirtualFile file : dir.getChildren()) {
          if (myFilesToDelete.contains(file.getNameWithoutExtension())) {
            DirectoryBasedStorage.deleteFile(file, this);
          }
        }
        myFilesToDelete.clear();
      }
      finally {
        token.finish();
      }
    }
  }

  @Nullable
  private VirtualFile getVirtualDir() {
    VirtualFile virtualFile = myDir;
    if (virtualFile == null) {
      myDir = virtualFile = LocalFileSystem.getInstance().findFileByIoFile(myIoDir);
    }
    return virtualFile;
  }

  @Override
  public File getRootDirectory() {
    return myIoDir;
  }

  private void deleteServerFile(@NotNull String path) {
    if (myProvider != null && myProvider.isEnabled()) {
      StorageUtil.delete(myProvider, getFileFullPath(path), myRoamingType);
    }
  }

  @Override
  protected void onSchemeDeleted(@NotNull Scheme toDelete) {
    super.onSchemeDeleted(toDelete);

    if (toDelete instanceof ExternalizableScheme) {
      ContainerUtilRt.addIfNotNull(myFilesToDelete, ((ExternalizableScheme)toDelete).getExternalInfo().getCurrentFileName());
    }
  }

  @Override
  protected void onSchemeAdded(@NotNull T scheme) {
    if (scheme instanceof ExternalizableScheme) {
      String fileName = ((ExternalizableScheme)scheme).getExternalInfo().getCurrentFileName();
      if (fileName != null) {
        myFilesToDelete.remove(fileName);
      }
      if (myProvider != null && myProvider.isEnabled()) {
        // do not save locally
        ((ExternalizableScheme)scheme).getExternalInfo().markRemote();
      }
    }
  }
}
