/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.impl.stores.DirectoryBasedStorage;
import com.intellij.openapi.components.impl.stores.DirectoryStorageData;
import com.intellij.openapi.components.impl.stores.StorageUtil;
import com.intellij.openapi.components.impl.stores.StreamProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.DocumentRunnable;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.tracker.VirtualFileTracker;
import com.intellij.util.PathUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.io.URLUtil;
import com.intellij.util.lang.CompoundRuntimeException;
import com.intellij.util.text.UniqueNameGenerator;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectObjectProcedure;
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
import java.net.URL;
import java.util.*;

public final class SchemesManagerImpl<T extends Scheme, E extends ExternalizableScheme> extends SchemesManager<T, E> {
  private static final Logger LOG = Logger.getInstance(SchemesManagerFactoryImpl.class);

  private final ArrayList<T> mySchemes = new ArrayList<T>();
  private volatile T myCurrentScheme;
  private String myCurrentSchemeName;

  private final String myFileSpec;
  private final SchemeProcessor<E> myProcessor;
  private final RoamingType myRoamingType;

  private final StreamProvider myProvider;
  private final File myIoDir;
  private VirtualFile myDir;

  private String mySchemeExtension = DirectoryStorageData.DEFAULT_EXT;
  private boolean myUpdateExtension;

  private final Set<String> myFilesToDelete = new THashSet<String>();

  private final THashMap<ExternalizableScheme, ExternalInfo> mySchemeToInfo = new THashMap<ExternalizableScheme, ExternalInfo>();

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
      myUpdateExtension = ((SchemeExtensionProvider)processor).isUpgradeNeeded();
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

          E readScheme = readSchemeFromFile(event.getFile(), true, false);
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
          if (event.getRequestor() == null) {
            if (event.getFile().isDirectory()) {
              VirtualFile dir = getVirtualDir();
              if (event.getFile().equals(dir)) {
                for (VirtualFile file : dir.getChildren()) {
                  if (isMy(file)) {
                    schemeCreatedExternally(file);
                  }
                }
              }
            }
            else if (isMy(event)) {
              schemeCreatedExternally(event.getFile());
            }
          }
        }

        private void schemeCreatedExternally(@NotNull VirtualFile file) {
          E readScheme = readSchemeFromFile(file, true, false);
          if (readScheme != null) {
            myProcessor.initScheme(readScheme);
            myProcessor.onSchemeAdded(readScheme);
          }
        }

        @Override
        public void fileDeleted(@NotNull VirtualFileEvent event) {
          if (event.getRequestor() == null) {
            if (event.getFile().isDirectory()) {
              VirtualFile dir = myDir;
              if (event.getFile().equals(dir)) {
                myDir = null;
                for (VirtualFile file : dir.getChildren()) {
                  if (isMy(file)) {
                    schemeDeletedExternally(file);
                  }
                }
              }
            }
            else if (isMy(event)) {
              schemeDeletedExternally(event.getFile());
            }
          }
        }

        private void schemeDeletedExternally(@NotNull VirtualFile file) {
          E scheme = findSchemeFor(file.getName());
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
      }, false, ApplicationManager.getApplication());
    }
  }

  @Override
  public void loadBundledScheme(@NotNull String resourceName, @NotNull Object requestor, @NotNull ThrowableConvertor<Element, T, Throwable> convertor) {
    try {
      URL url = requestor instanceof AbstractExtensionPointBean
                ? (((AbstractExtensionPointBean)requestor).getLoaderForClass().getResource(resourceName))
                : DecodeDefaultsUtil.getDefaults(requestor, resourceName);
      if (url == null) {
        // Error shouldn't occur during this operation thus we report error instead of info
        LOG.error("Cannot read scheme from " + resourceName);
        return;
      }
      Element element = JDOMUtil.load(URLUtil.openStream(url));
      T scheme = convertor.convert(element);

      if (scheme instanceof ExternalizableScheme) {
        ExternalInfo info = getExternalInfo((ExternalizableScheme)scheme);
        info.setHash(JDOMUtil.getTreeHash(element, true));
        info.setPreviouslySavedName(scheme.getName());
        info.setCurrentFileName(PathUtilRt.getFileName(resourceName));
      }

      addNewScheme(scheme, false);
    }
    catch (Throwable e) {
      LOG.error("Cannot read scheme from " + resourceName, e);
    }
  }

  private boolean isMy(@NotNull VirtualFileEvent event) {
    return isMy(event.getFile());
  }

  private boolean isMy(@NotNull VirtualFile file) {
    return StringUtilRt.endsWithIgnoreCase(file.getNameSequence(), mySchemeExtension);
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
          E scheme = readSchemeFromFile(file, false, true);
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

  @NotNull
  private ExternalInfo getExternalInfo(@NotNull ExternalizableScheme scheme) {
    ExternalInfo info = mySchemeToInfo.get(scheme);
    if (info == null) {
      info = new ExternalInfo();
      mySchemeToInfo.put(scheme, info);
    }
    return info;
  }

  private E findSchemeFor(@NotNull String ioFileName) {
    for (T scheme : mySchemes) {
      if (scheme instanceof ExternalizableScheme) {
        if (ioFileName.equals((getCurrentFileName((ExternalizableScheme)scheme)) + mySchemeExtension)) {
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

        E scheme = readScheme(element, true);
        boolean fileRenamed = false;
        assert scheme != null;
        T existing = findSchemeByName(scheme.getName());
        if (existing instanceof ExternalizableScheme) {
          String currentFileName = getCurrentFileName((ExternalizableScheme)existing);
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
        getExternalInfo(scheme).markRemote();
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
        String name = getCurrentFileName((ExternalizableScheme)scheme);
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

  @Nullable
  private String getCurrentFileName(@NotNull ExternalizableScheme scheme) {
    ExternalInfo info = mySchemeToInfo.get(scheme);
    return info == null ? null : info.getCurrentFileName();
  }

  @NotNull
  private Collection<String> collectAllFileNames() {
    Set<String> result = new THashSet<String>();
    for (T scheme : mySchemes) {
      if (scheme instanceof ExternalizableScheme) {
        ContainerUtilRt.addIfNotNull(result, getCurrentFileName((ExternalizableScheme)scheme));
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
      if (existing instanceof ExternalizableScheme) {
        //noinspection unchecked,CastConflictsWithInstanceof
        myProcessor.onSchemeDeleted((E)existing);
      }
    }

    //noinspection unchecked
    addNewScheme((T)scheme, true);
    ExternalInfo info = getExternalInfo(scheme);
    info.setPreviouslySavedName(scheme.getName());
    info.setCurrentFileName(fileNameWithoutExtension);
  }

  private boolean canRead(@NotNull VirtualFile file) {
    if (file.isDirectory()) {
      return false;
    }

    if (myUpdateExtension && !DirectoryStorageData.DEFAULT_EXT.equals(mySchemeExtension) && DirectoryStorageData.isStorageFile(file)) {
      // read file.DEFAULT_EXT only if file.CUSTOM_EXT doesn't exists
      return myDir.findChild(file.getNameSequence() + mySchemeExtension) == null;
    }
    else {
      return StringUtilRt.endsWithIgnoreCase(file.getNameSequence(), mySchemeExtension);
    }
  }

  @Nullable
  private E readSchemeFromFile(@NotNull final VirtualFile file, boolean forceAdd, boolean duringLoad) {
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
          File initialIoFile = new File(myIoDir, file.getName());
          if (initialIoFile.isFile()) {
            FileUtil.copy(initialIoFile, new File(myIoDir, file.getName() + ".copy"));
          }
        }
        catch (IOException e1) {
          LOG.error(e1);
        }
        LOG.error("Error reading file " + file.getPath() + ": " + e.getMessage());
        return null;
      }

      E scheme = readScheme(element, duringLoad);
      if (scheme != null) {
        loadScheme(scheme, forceAdd, file.getNameSequence());
      }
      return scheme;
    }
    catch (final Exception e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                          String msg = "Cannot read scheme " + file.getName() + "  from '" + myFileSpec + "': " + e.getMessage();
                                                          LOG.info(msg, e);
                                                          Messages.showErrorDialog(msg, "Load Settings");
                                                        }
                                                      }
      );
      return null;
    }
  }

  @Nullable
  private E readScheme(@NotNull Element element, boolean duringLoad) throws InvalidDataException, IOException, JDOMException {
    E scheme;
    if (myProcessor instanceof BaseSchemeProcessor) {
      scheme = ((BaseSchemeProcessor<E>)myProcessor).readScheme(element, duringLoad);
    }
    else {
      //noinspection deprecation
      scheme = myProcessor.readScheme(new Document((Element)element.detach()));
    }
    if (scheme != null) {
      getExternalInfo(scheme).setHash(JDOMUtil.getTreeHash(element, true));
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
  public void save() {
    boolean hasSchemes = false;
    UniqueNameGenerator nameGenerator = new UniqueNameGenerator();
    List<E> schemesToSave = new SmartList<E>();
    for (T scheme : mySchemes) {
      if (scheme instanceof ExternalizableScheme) {
        //noinspection CastConflictsWithInstanceof,unchecked
        E eScheme = (E)scheme;
        BaseSchemeProcessor.State state;
        if (myProcessor instanceof BaseSchemeProcessor) {
          state = ((BaseSchemeProcessor<E>)myProcessor).getState(eScheme);
        }
        else {
          //noinspection deprecation
          state = myProcessor.shouldBeSaved(eScheme) ? BaseSchemeProcessor.State.POSSIBLY_CHANGED : BaseSchemeProcessor.State.NON_PERSISTENT;
        }

        if (state == BaseSchemeProcessor.State.NON_PERSISTENT) {
          continue;
        }

        hasSchemes = true;

        if (state != BaseSchemeProcessor.State.UNCHANGED) {
          schemesToSave.add(eScheme);
        }

        String fileName = getCurrentFileName(eScheme);
        if (fileName != null && !isRenamed(eScheme)) {
          nameGenerator.addExistingName(fileName);
        }
      }
    }

    List<Throwable> errors = null;

    for (E scheme : schemesToSave) {
      try {
        saveScheme(scheme, nameGenerator);
      }
      catch (Throwable e) {
        if (errors == null) {
          errors = new SmartList<Throwable>();
        }
        errors.add(e);
      }
    }

    VirtualFile dir = getVirtualDir();
    errors = deleteFiles(dir, errors);

    if (!hasSchemes && dir != null) {
      LOG.info("No schemes to save, directory " + dir.getName() + " will be removed");

      AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(DocumentRunnable.IgnoreDocumentRunnable.class);
      try {
        boolean remove = true;
        for (VirtualFile file : dir.getChildren()) {
          if (StringUtilRt.endsWithIgnoreCase(file.getNameSequence(), mySchemeExtension)) {
            LOG.info("Directory " + dir.getName() + " cannot be removed - scheme " + file.getName() + " exists");
            remove = false;
            break;
          }
        }

        if (remove) {
          LOG.info("Remove schemes directory " + dir.getName());
          try {
            StorageUtil.deleteFile(this, dir);
            myDir = null;
          }
          catch (Throwable e) {
            if (errors == null) {
              errors = new SmartList<Throwable>();
            }
            errors.add(e);
          }
        }
      }
      finally {
        token.finish();
      }
    }

    CompoundRuntimeException.doThrow(errors);
  }

  private void saveScheme(@NotNull E scheme, @NotNull UniqueNameGenerator nameGenerator) throws WriteExternalException, IOException {
    ExternalInfo externalInfo = getExternalInfo(scheme);
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

    int newHash = JDOMUtil.getTreeHash(element, true);
    if (currentFileNameWithoutExtension == fileNameWithoutExtension && newHash == externalInfo.getHash()) {
      return;
    }

    // file will be overwritten, so, we don't need to delete it
    myFilesToDelete.remove(fileNameWithoutExtension);

    // stream provider always use LF separator
    final BufferExposingByteArrayOutputStream byteOut = StorageUtil.writeToBytes(element, "\n");

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

      AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(DocumentRunnable.IgnoreDocumentRunnable.class);
      try {
        OutputStream out = file.getOutputStream(this);
        try {
          byteOut.writeTo(out);
        }
        finally {
          out.close();
        }
      }
      finally {
        token.finish();
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

  private boolean isRenamed(@NotNull ExternalizableScheme scheme) {
    ExternalInfo info = mySchemeToInfo.get(scheme);
    return info != null && !scheme.getName().equals(info.getPreviouslySavedName());
  }

  @Nullable
  private List<Throwable> deleteFiles(@Nullable VirtualFile dir, List<Throwable> errors) {
    if (myFilesToDelete.isEmpty()) {
      return errors;
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
            try {
              file.delete(this);
            }
            catch (IOException e) {
              if (errors == null) {
                errors = new SmartList<Throwable>();
              }
              errors.add(e);
            }
          }
        }
        myFilesToDelete.clear();
      }
      finally {
        token.finish();
      }
    }
    return errors;
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

  private void schemeDeleted(@NotNull Scheme scheme) {
    if (myCurrentScheme == scheme) {
      myCurrentScheme = null;
    }

    if (scheme instanceof ExternalizableScheme) {
      ExternalInfo info = mySchemeToInfo.get((ExternalizableScheme)scheme);
      if (info != null) {
        ContainerUtilRt.addIfNotNull(myFilesToDelete, info.getCurrentFileName());
      }
    }
  }

  private void schemeAdded(@NotNull T scheme) {
    if (!(scheme instanceof ExternalizableScheme)) {
      return;
    }

    ExternalInfo info = mySchemeToInfo.get((ExternalizableScheme)scheme);
    if (info != null) {
      String fileName = info.getCurrentFileName();
      if (fileName != null) {
        myFilesToDelete.remove(fileName);
      }
    }

    // todo is flag "remote" really need?
    if (myProvider != null && myProvider.isEnabled()) {
      // do not save locally
      getExternalInfo((ExternalizableScheme)scheme).markRemote();
    }
  }

  @Override
  public void setSchemes(@NotNull final List<T> schemes, @Nullable Condition<T> removeCondition) {
    if (removeCondition == null) {
      doRemoveAll();
    }
    else {
      for (int i = 0; i < schemes.size(); i++) {
        T scheme = schemes.get(i);
        if (removeCondition.value(scheme)) {
          schemeDeleted(scheme);
          mySchemes.remove(i);
        }
      }
    }

    mySchemeToInfo.retainEntries(new TObjectObjectProcedure<ExternalizableScheme, ExternalInfo>() {
      @Override
      public boolean execute(ExternalizableScheme scheme, ExternalInfo b) {
        // yes, by equals, not by identity
        //noinspection SuspiciousMethodCalls
        return schemes.contains(scheme);
      }
    });

    mySchemes.ensureCapacity(schemes.size());
    for (T scheme : schemes) {
      mySchemes.add(scheme);
      schemeAdded(scheme);
    }

    if (myCurrentSchemeName != null) {
      myCurrentScheme = findSchemeByName(myCurrentSchemeName);
      if (myCurrentScheme != null) {
        return;
      }
    }

    myCurrentScheme = mySchemes.isEmpty() ? null : mySchemes.get(0);
    myCurrentSchemeName = myCurrentScheme == null ? null : myCurrentScheme.getName();
  }

  @Override
  public void addNewScheme(@NotNull T scheme, boolean replaceExisting) {
    int toReplace = -1;
    for (int i = 0; i < mySchemes.size(); i++) {
      T existingScheme = mySchemes.get(i);
      if (existingScheme.getName().equals(scheme.getName())) {
        toReplace = i;
        if (replaceExisting && existingScheme instanceof ExternalizableScheme && scheme instanceof ExternalizableScheme) {
          ExternalInfo newInfo = mySchemeToInfo.get((ExternalizableScheme)existingScheme);
          if (newInfo == null || newInfo.getCurrentFileName() == null) {
            ExternalInfo oldInfo = mySchemeToInfo.get((ExternalizableScheme)existingScheme);
            if (oldInfo != null) {
              getExternalInfo((ExternalizableScheme)scheme).setCurrentFileName(oldInfo.getCurrentFileName());
            }
          }
        }
        break;
      }
    }
    if (toReplace == -1) {
      mySchemes.add(scheme);
    }
    else if (replaceExisting || !(scheme instanceof ExternalizableScheme)) {
      mySchemes.set(toReplace, scheme);
    }
    else {
      //noinspection unchecked
      renameScheme((ExternalizableScheme)scheme, UniqueNameGenerator.generateUniqueName(scheme.getName(), collectExistingNames(mySchemes)));
      mySchemes.add(scheme);
    }

    schemeAdded(scheme);
    checkCurrentScheme(scheme);
  }

  private void checkCurrentScheme(@NotNull Scheme scheme) {
    if (myCurrentScheme == null && scheme.getName().equals(myCurrentSchemeName)) {
      //noinspection unchecked
      myCurrentScheme = (T)scheme;
    }
  }

  @NotNull
  private Collection<String> collectExistingNames(@NotNull Collection<T> schemes) {
    Set<String> result = new THashSet<String>(schemes.size());
    for (T scheme : schemes) {
      result.add(scheme.getName());
    }
    return result;
  }

  @Override
  public void clearAllSchemes() {
    doRemoveAll();
  }

  private void doRemoveAll() {
    for (T myScheme : mySchemes) {
      schemeDeleted(myScheme);
    }
    mySchemes.clear();
  }

  @Override
  @NotNull
  public List<T> getAllSchemes() {
    return Collections.unmodifiableList(mySchemes);
  }

  @Override
  @Nullable
  public T findSchemeByName(@NotNull String schemeName) {
    for (T scheme : mySchemes) {
      if (scheme.getName().equals(schemeName)) {
        return scheme;
      }
    }
    return null;
  }

  @Override
  public void setCurrentSchemeName(@Nullable String schemeName) {
    myCurrentSchemeName = schemeName;
    myCurrentScheme = schemeName == null ? null : findSchemeByName(schemeName);
  }

  @Override
  @Nullable
  public T getCurrentScheme() {
    T currentScheme = myCurrentScheme;
    return currentScheme == null ? null : findSchemeByName(currentScheme.getName());
  }

  @Override
  public void removeScheme(@NotNull T scheme) {
    for (int i = 0, n = mySchemes.size(); i < n; i++) {
      T s = mySchemes.get(i);
      if (scheme.getName().equals(s.getName())) {
        schemeDeleted(s);
        mySchemes.remove(i);
        break;
      }
    }
  }

  @Override
  @NotNull
  public Collection<String> getAllSchemeNames() {
    List<String> names = new ArrayList<String>(mySchemes.size());
    for (T scheme : mySchemes) {
      names.add(scheme.getName());
    }
    return names;
  }

  private static void renameScheme(@NotNull ExternalizableScheme scheme, @NotNull String newName) {
    if (!newName.equals(scheme.getName())) {
      scheme.setName(newName);
      LOG.assertTrue(newName.equals(scheme.getName()));
    }
  }
}
