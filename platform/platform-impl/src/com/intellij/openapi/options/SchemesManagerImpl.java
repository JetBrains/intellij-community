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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
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
import gnu.trove.TObjectProcedure;
import org.jdom.Document;
import org.jdom.Element;
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
  @Nullable
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

          E readScheme = readSchemeFromFile(event.getFile(), false, null);
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
          E readScheme = readSchemeFromFile(file, false, null);
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
        info.hash = JDOMUtil.getTreeHash(element, true);
        info.previouslySavedName = scheme.getName();
        info.fileName = PathUtilRt.getFileName(resourceName);
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
    final Map<String, E> result = new LinkedHashMap<String, E>();
    if (myProvider != null && myProvider.isEnabled()) {
      myProvider.processChildren(myFileSpec, myRoamingType, new Condition<String>() {
        @Override
        public boolean value(@NotNull String name) {
          return canRead(name);
        }
      }, new StreamProvider.ChildrenProcessor() {
        @Override
        public boolean process(@NotNull String name, @NotNull InputStream input) {
          loadScheme(name, input, true, result);
          return true;
        }
      });
    }
    else {
      VirtualFile dir = getVirtualDir();
      VirtualFile[] files = dir == null ? null : dir.getChildren();
      if (files != null) {
        for (VirtualFile file : files) {
          readSchemeFromFile(file, true, result);
        }
      }
    }

    Collection<E> list = result.values();
    for (E scheme : list) {
      myProcessor.initScheme(scheme);
    }
    return list;
  }

  public void reload() {
    // we must not remove non-persistent (e.g. predefined) schemes, because we cannot load it (obviously)
    for (int i = mySchemes.size() - 1; i >= 0; i--) {
      T scheme = mySchemes.get(i);
      //noinspection unchecked
      if (scheme instanceof ExternalizableScheme && getState(((E)scheme)) != BaseSchemeProcessor.State.NON_PERSISTENT) {
        mySchemes.remove(i);
        if (scheme == myCurrentScheme) {
          myCurrentScheme = null;
        }
      }
    }

    retainExternalInfo(mySchemes);

    loadSchemes();
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

  @Nullable
  private E findSchemeFor(@NotNull String ioFileName) {
    for (T scheme : mySchemes) {
      if (scheme instanceof ExternalizableScheme) {
        if (ioFileName.equals((getFileName((ExternalizableScheme)scheme)) + mySchemeExtension)) {
          //noinspection CastConflictsWithInstanceof,unchecked
          return (E)scheme;
        }
      }
    }
    return null;
  }

  @Nullable
  private E loadScheme(@NotNull CharSequence name, @NotNull InputStream input, boolean duringLoad, @Nullable Map<String, E> result) {
    try {
      Element element = JDOMUtil.load(input);
      E scheme;
      if (myProcessor instanceof BaseSchemeProcessor) {
        scheme = ((BaseSchemeProcessor<E>)myProcessor).readScheme(element, duringLoad);
      }
      else {
        //noinspection deprecation
        scheme = myProcessor.readScheme(new Document((Element)element.detach()));
      }

      if (scheme == null) {
        return null;
      }

      String fileNameWithoutExtension = createFileName(name);
      if (duringLoad) {
        if (myFilesToDelete.contains(fileNameWithoutExtension)) {
          return null;
        }

        if (result == null ? findSchemeByName(scheme.getName()) != null : result.containsKey(scheme.getName())) {
          // We don't load scheme with duplicated name - if we generate unique name for it, it will be saved then with new name.
          // It is not what all can expect. Such situation in most cases indicates error on previous level, so, we just warn about it.
          LOG.warn("Scheme file " + name + " is ignored because defines duplicated name " + scheme.getName());
          return null;
        }
      }

      ExternalInfo info = getExternalInfo(scheme);
      info.hash = JDOMUtil.getTreeHash(element, true);
      info.previouslySavedName = scheme.getName();
      info.fileName = fileNameWithoutExtension;

      //noinspection unchecked
      T s = (T)scheme;
      if (duringLoad) {
        mySchemes.add(s);
      }
      else {
        addScheme(s);
      }

      if (result != null) {
        result.put(scheme.getName(), scheme);
      }
      return scheme;
    }
    catch (Exception e) {
      LOG.error("Cannot read scheme " + name, e);
    }
    return null;
  }

  @Nullable
  private String getFileName(@NotNull ExternalizableScheme scheme) {
    ExternalInfo info = mySchemeToInfo.get(scheme);
    return info == null ? null : info.fileName;
  }

  private boolean canRead(@NotNull VirtualFile file) {
    return !file.isDirectory() && canRead(file.getNameSequence());
  }

  private boolean canRead(@NotNull CharSequence name) {
    if (myUpdateExtension && !DirectoryStorageData.DEFAULT_EXT.equals(mySchemeExtension) && StringUtilRt.endsWithIgnoreCase(name, DirectoryStorageData.DEFAULT_EXT)) {
      // read file.DEFAULT_EXT only if file.CUSTOM_EXT doesn't exists
      return myDir.findChild(name + mySchemeExtension) == null;
    }
    else {
      return StringUtilRt.endsWithIgnoreCase(name, mySchemeExtension);
    }
  }

  @Nullable
  private E readSchemeFromFile(@NotNull VirtualFile file, boolean duringLoad, @Nullable Map<String, E> result) {
    if (!canRead(file)) {
      return null;
    }

    try {
      return loadScheme(file.getNameSequence(), file.getInputStream(), duringLoad, result);
    }
    catch (IOException e) {
      LOG.error("Cannot read scheme " + file.getNameSequence(), e);
    }
    return null;
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
        BaseSchemeProcessor.State state = getState(eScheme);
        if (state == BaseSchemeProcessor.State.NON_PERSISTENT) {
          continue;
        }

        hasSchemes = true;

        if (state != BaseSchemeProcessor.State.UNCHANGED) {
          schemesToSave.add(eScheme);
        }

        String fileName = getFileName(eScheme);
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

  @NotNull
  private BaseSchemeProcessor.State getState(@NotNull E scheme) {
    if (myProcessor instanceof BaseSchemeProcessor) {
      return ((BaseSchemeProcessor<E>)myProcessor).getState(scheme);
    }
    else {
      //noinspection deprecation
      return myProcessor.shouldBeSaved(scheme) ? BaseSchemeProcessor.State.POSSIBLY_CHANGED : BaseSchemeProcessor.State.NON_PERSISTENT;
    }
  }

  private void saveScheme(@NotNull E scheme, @NotNull UniqueNameGenerator nameGenerator) throws WriteExternalException, IOException {
    ExternalInfo externalInfo = getExternalInfo(scheme);
    String currentFileNameWithoutExtension = externalInfo.fileName;
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
    if (currentFileNameWithoutExtension == fileNameWithoutExtension && newHash == externalInfo.hash) {
      return;
    }

    // file will be overwritten, so, we don't need to delete it
    myFilesToDelete.remove(fileNameWithoutExtension);

    // stream provider always use LF separator
    final BufferExposingByteArrayOutputStream byteOut = StorageUtil.writeToBytes(element, "\n");

    String providerPath;
    if (myProvider != null && myProvider.isEnabled()) {
      providerPath = getFileFullPath(fileName);
      if (!myProvider.isApplicable(providerPath, myRoamingType)) {
        providerPath = null;
      }
    }
    else {
      providerPath = null;
    }

    // if another new scheme uses old name of this scheme, so, we must not delete it (as part of rename operation)
    boolean renamed = currentFileNameWithoutExtension != null && fileNameWithoutExtension != currentFileNameWithoutExtension && nameGenerator.value(currentFileNameWithoutExtension);
    if (providerPath == null) {
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

    externalInfo.hash = newHash;
    externalInfo.previouslySavedName = scheme.getName();
    externalInfo.fileName = createFileName(fileName);

    if (providerPath != null) {
      myProvider.saveContent(providerPath, byteOut.getInternalBuffer(), byteOut.size(), myRoamingType);
    }
  }

  private boolean isRenamed(@NotNull ExternalizableScheme scheme) {
    ExternalInfo info = mySchemeToInfo.get(scheme);
    return info != null && !scheme.getName().equals(info.previouslySavedName);
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

  private void schemeAdded(@NotNull T scheme) {
    if (!(scheme instanceof ExternalizableScheme)) {
      return;
    }

    String fileName = getFileName((ExternalizableScheme)scheme);
    if (fileName != null) {
      myFilesToDelete.remove(fileName);
    }
  }

  @Override
  public void setSchemes(@NotNull final List<T> schemes, @Nullable Condition<T> removeCondition) {
    if (removeCondition == null) {
      mySchemes.clear();
    }
    else {
      for (int i = schemes.size() - 1; i >= 0; i--) {
        T scheme = schemes.get(i);
        if (removeCondition.value(scheme)) {
          mySchemes.remove(i);
        }
      }
    }

    retainExternalInfo(schemes);

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

  private void retainExternalInfo(@NotNull final List<T> schemes) {
    mySchemeToInfo.retainEntries(new TObjectObjectProcedure<ExternalizableScheme, ExternalInfo>() {
      @Override
      public boolean execute(ExternalizableScheme scheme, ExternalInfo info) {
        // yes, by equals, not by identity
        //noinspection SuspiciousMethodCalls
        boolean keep = schemes.contains(scheme);
        if (!keep && info.fileName != null) {
          myFilesToDelete.remove(info.fileName);
        }
        return keep;
      }
    });
  }

  @Override
  public void addNewScheme(@NotNull T scheme, boolean replaceExisting) {
    int toReplace = -1;
    for (int i = 0; i < mySchemes.size(); i++) {
      T existing = mySchemes.get(i);
      if (existing.getName().equals(scheme.getName())) {
        if (!Comparing.equal(existing.getClass(), scheme.getClass())) {
          LOG.warn("'" + scheme.getName() + "' " + existing.getClass().getSimpleName() + " replaced with " + scheme.getClass().getSimpleName());
        }

        toReplace = i;
        if (replaceExisting && existing instanceof ExternalizableScheme) {
          ExternalInfo oldInfo = mySchemeToInfo.remove((ExternalizableScheme)existing);
          if (scheme instanceof ExternalizableScheme) {
            ExternalInfo newInfo = mySchemeToInfo.get((ExternalizableScheme)scheme);
            if (newInfo == null || newInfo.fileName == null) {
              if (oldInfo != null && oldInfo.fileName != null) {
                getExternalInfo((ExternalizableScheme)scheme).fileName = oldInfo.fileName;
              }
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
    mySchemeToInfo.forEachValue(new TObjectProcedure<ExternalInfo>() {
      @Override
      public boolean execute(ExternalInfo info) {
        ContainerUtilRt.addIfNotNull(myFilesToDelete, info.fileName);
        return true;
      }
    });

    myCurrentScheme = null;
    mySchemes.clear();
    mySchemeToInfo.clear();
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
    T scheme = myCurrentScheme;
    if (scheme == null && myCurrentSchemeName != null) {
      scheme = findSchemeByName(myCurrentSchemeName);
      if (scheme != null) {
        myCurrentScheme = scheme;
      }
    }
    return scheme;
  }

  @Override
  public void removeScheme(@NotNull T scheme) {
    for (int i = 0, n = mySchemes.size(); i < n; i++) {
      T s = mySchemes.get(i);
      if (scheme.getName().equals(s.getName())) {
        if (myCurrentScheme == s) {
          myCurrentScheme = null;
        }

        if (s instanceof ExternalizableScheme) {
          ExternalInfo info = mySchemeToInfo.remove((ExternalizableScheme)s);
          if (info != null) {
            ContainerUtilRt.addIfNotNull(myFilesToDelete, info.fileName);
          }
        }
        mySchemes.remove(i);
        break;
      }
    }
  }

  @Override
  @NotNull
  public Collection<String> getAllSchemeNames() {
    if (mySchemes.isEmpty()) {
      return Collections.emptyList();
    }

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

  private static final class ExternalInfo {
    // we keep it to detect rename
    private String previouslySavedName;

    private String fileName;
    private int hash;

    @Override
    public String toString() {
      return fileName;
    }
  }
}
