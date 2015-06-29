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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.impl.stores.DirectoryBasedStorage;
import com.intellij.openapi.components.impl.stores.DirectoryStorageData;
import com.intellij.openapi.components.impl.stores.StorageUtil;
import com.intellij.openapi.components.impl.stores.StreamProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.tracker.VirtualFileTracker;
import com.intellij.util.PathUtil;
import com.intellij.util.PathUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.io.URLUtil;
import com.intellij.util.lang.CompoundRuntimeException;
import com.intellij.util.text.UniqueNameGenerator;
import gnu.trove.*;
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

public final class SchemesManagerImpl<T extends Scheme, E extends ExternalizableScheme> extends SchemesManager<T, E> implements SafeWriteRequestor {
  private static final Logger LOG = Logger.getInstance(SchemesManagerFactoryImpl.class);

  private final ArrayList<T> mySchemes = new ArrayList<T>();
  private volatile T myCurrentScheme;
  @Nullable
  private String myCurrentSchemeName;

  private final String myFileSpec;
  private final SchemeProcessor<E> myProcessor;
  private final RoamingType myRoamingType;

  private final StreamProvider provider;
  private final File myIoDir;
  private VirtualFile myDir;

  private final String mySchemeExtension;
  private final boolean myUpdateExtension;

  private final Set<String> filesToDelete = new THashSet<String>();

  // scheme could be changed - so, hashcode will be changed - we must use identity hashing strategy
  @SuppressWarnings("unchecked")
  private final THashMap<ExternalizableScheme, ExternalInfo> schemeToInfo = new THashMap<ExternalizableScheme, ExternalInfo>(TObjectHashingStrategy.IDENTITY);

  public SchemesManagerImpl(@NotNull String fileSpec,
                            @NotNull SchemeProcessor<E> processor,
                            @NotNull RoamingType roamingType,
                            @Nullable StreamProvider provider,
                            @NotNull File baseDir) {
    myFileSpec = fileSpec;
    myProcessor = processor;
    myRoamingType = roamingType;
    this.provider = provider;
    myIoDir = baseDir;
    if (processor instanceof SchemeExtensionProvider) {
      mySchemeExtension = ((SchemeExtensionProvider)processor).getSchemeExtension();
      myUpdateExtension = ((SchemeExtensionProvider)processor).isUpgradeNeeded();
    }
    else {
      mySchemeExtension = DirectoryStorageData.DEFAULT_EXT;
      myUpdateExtension = false;
    }

    Application application = ApplicationManager.getApplication();
    VirtualFileTracker virtualFileTracker = application == null ? null : ServiceManager.getService(VirtualFileTracker.class);
    if (virtualFileTracker != null) {
      final String baseDirPath = myIoDir.getAbsolutePath().replace(File.separatorChar, '/');
      virtualFileTracker.addTracker(LocalFileSystem.PROTOCOL_PREFIX + baseDirPath, new VirtualFileAdapter() {
        @Override
        public void contentsChanged(@NotNull VirtualFileEvent event) {
          if (event.getRequestor() != null || !isMy(event)) {
            return;
          }

          E scheme = findExternalizableSchemeByFileName(event.getFile().getName());
          T oldCurrentScheme = null;
          if (scheme != null) {
            oldCurrentScheme = getCurrentScheme();
            //noinspection unchecked
            removeScheme((T)scheme);
            myProcessor.onSchemeDeleted(scheme);
          }

          E readScheme = readSchemeFromFile(event.getFile(), null);
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
              VirtualFile dir = getDirectory();
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
          E readScheme = readSchemeFromFile(file, null);
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
          E scheme = findExternalizableSchemeByFileName(file.getName());
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
      }, false, application);
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
        String fileName = PathUtilRt.getFileName(url.getPath());
        String extension = getFileExtension(fileName, true);
        ExternalInfo info = new ExternalInfo(fileName.substring(0, fileName.length() - extension.length()), extension);
        info.hash = JDOMUtil.getTreeHash(element, true);
        info.schemeName = scheme.getName();
        ExternalInfo oldInfo = schemeToInfo.put((ExternalizableScheme)scheme, info);
        LOG.assertTrue(oldInfo == null);
      }

      addNewScheme(scheme, false);
    }
    catch (Throwable e) {
      LOG.error("Cannot read scheme from " + resourceName, e);
    }
  }

  @NotNull
  private String getFileExtension(@NotNull CharSequence fileName, boolean allowAny) {
    String extension;
    if (StringUtilRt.endsWithIgnoreCase(fileName, mySchemeExtension)) {
      extension = mySchemeExtension;
    }
    else if (StringUtilRt.endsWithIgnoreCase(fileName, DirectoryStorageData.DEFAULT_EXT)) {
      extension = DirectoryStorageData.DEFAULT_EXT;
    }
    else if (allowAny) {
      extension = PathUtil.getFileExtension(fileName.toString());
      LOG.assertTrue(extension != null);
    }
    else {
      throw new IllegalStateException("Scheme file extension " + fileName + " is unknown, must be filtered out");
    }
    return extension;
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
    if (provider != null && provider.isEnabled()) {
      provider.processChildren(myFileSpec, myRoamingType, new Condition<String>() {
        @Override
        public boolean value(@NotNull String name) {
          return canRead(name);
        }
      }, new StreamProvider.ChildrenProcessor() {
        @Override
        public boolean process(@NotNull String name, @NotNull InputStream input) {
          loadScheme(name, input, result);
          return true;
        }
      });
    }
    else {
      VirtualFile dir = getDirectory();
      VirtualFile[] files = dir == null ? null : dir.getChildren();
      if (files != null) {
        for (VirtualFile file : files) {
          readSchemeFromFile(file, result);
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

  @Nullable
  private E findExternalizableSchemeByFileName(@NotNull String fileName) {
    for (T scheme : mySchemes) {
      if (scheme instanceof ExternalizableScheme) {
        if (fileName.equals((getFileName((ExternalizableScheme)scheme)) + mySchemeExtension)) {
          //noinspection CastConflictsWithInstanceof,unchecked
          return (E)scheme;
        }
      }
    }
    return null;
  }

  private boolean isOverwriteOnLoad(@NotNull T existingScheme) {
    if (existingScheme instanceof ExternalizableScheme) {
      ExternalInfo info = schemeToInfo.get(existingScheme);
      if (info != null && !mySchemeExtension.equals(info.fileExtension)) {
        // scheme from file with old extension, so, we must ignore it
        return true;
      }
    }
    return false;
  }

  @Nullable
  private E loadScheme(@NotNull CharSequence fileName, @NotNull InputStream input, @Nullable Map<String, E> result) {
    boolean duringLoad = result != null;
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

      String extension = getFileExtension(fileName, false);
      String fileNameWithoutExtension = fileName.subSequence(0, fileName.length() - extension.length()).toString();
      if (duringLoad) {
        if (filesToDelete.contains(fileName.toString())) {
          LOG.warn("Scheme file " + fileName + " is not loaded because marked to delete");
          return null;
        }

        T existingScheme = findSchemeByName(scheme.getName());
        if (existingScheme != null) {
          if (isOverwriteOnLoad(existingScheme)) {
            removeScheme(existingScheme);
          }
          else {
            // We don't load scheme with duplicated name - if we generate unique name for it, it will be saved then with new name.
            // It is not what all can expect. Such situation in most cases indicates error on previous level, so, we just warn about it.
            LOG.warn("Scheme file " + fileName + " is not loaded because defines duplicated name " + scheme.getName());
            return null;
          }
        }
      }

      ExternalInfo info = schemeToInfo.get(scheme);
      if (info == null) {
        info = new ExternalInfo(fileNameWithoutExtension, extension);
        schemeToInfo.put(scheme, info);
      }
      else {
        info.setFileNameWithoutExtension(fileNameWithoutExtension, extension);
      }
      info.hash = JDOMUtil.getTreeHash(element, true);
      info.schemeName = scheme.getName();

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
      LOG.error("Cannot read scheme " + fileName, e);
    }
    return null;
  }

  @Nullable
  private String getFileName(@NotNull ExternalizableScheme scheme) {
    ExternalInfo info = schemeToInfo.get(scheme);
    return info == null ? null : info.fileNameWithoutExtension;
  }

  private boolean canRead(@NotNull CharSequence name) {
    return myUpdateExtension && StringUtilRt.endsWithIgnoreCase(name, DirectoryStorageData.DEFAULT_EXT) || StringUtilRt.endsWithIgnoreCase(name, mySchemeExtension);
  }

  @Nullable
  private E readSchemeFromFile(@NotNull VirtualFile file, @Nullable Map<String, E> result) {
    CharSequence fileName = file.getNameSequence();
    if (file.isDirectory() || !canRead(fileName)) {
      return null;
    }

    try {
      return loadScheme(fileName, file.getInputStream(), result);
    }
    catch (IOException e) {
      LOG.error("Cannot read scheme " + fileName, e);
    }
    return null;
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

    VirtualFile dir = getDirectory();
    errors = deleteFiles(dir, errors);

    if (!hasSchemes && dir != null) {
      errors = removeDirectoryIfEmpty(dir, errors);
    }

    CompoundRuntimeException.doThrow(errors);
  }

  @Nullable
  private List<Throwable> removeDirectoryIfEmpty(@NotNull VirtualFile dir, @Nullable List<Throwable> errors) {
    for (VirtualFile file : dir.getChildren()) {
      if (!file.is(VFileProperty.HIDDEN)) {
        LOG.info("Directory " + dir.getNameSequence() + " is not deleted: at least one file " + file.getNameSequence() + " exists");
        return errors;
      }
    }

    LOG.info("Remove schemes directory " + dir.getNameSequence());
    myDir = null;

    AccessToken token = WriteAction.start();
    try {
      dir.delete(this);
    }
    catch (Throwable e) {
      if (errors == null) {
        errors = new SmartList<Throwable>();
      }
      errors.add(e);
    }
    finally {
      token.finish();
    }
    return errors;
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
    @Nullable
    ExternalInfo externalInfo = schemeToInfo.get(scheme);
    String currentFileNameWithoutExtension = externalInfo == null ? null : externalInfo.fileNameWithoutExtension;
    Parent parent = myProcessor.writeScheme(scheme);
    Element element = parent == null || parent instanceof Element ? (Element)parent : ((Document)parent).detachRootElement();
    if (JDOMUtil.isEmpty(element)) {
      if (externalInfo != null) {
        scheduleDelete(externalInfo);
      }
      return;
    }

    String fileNameWithoutExtension = currentFileNameWithoutExtension;
    if (fileNameWithoutExtension == null || isRenamed(scheme)) {
      fileNameWithoutExtension = nameGenerator.generateUniqueName(FileUtil.sanitizeName(scheme.getName()));
    }

    int newHash = JDOMUtil.getTreeHash(element, true);
    if (externalInfo != null && currentFileNameWithoutExtension == fileNameWithoutExtension && newHash == externalInfo.hash) {
      return;
    }

    String fileName = fileNameWithoutExtension + mySchemeExtension;
    // file will be overwritten, so, we don't need to delete it
    filesToDelete.remove(fileName);

    // stream provider always use LF separator
    BufferExposingByteArrayOutputStream byteOut = StorageUtil.writeToBytes(element, "\n");

    String providerPath;
    if (provider != null && provider.isEnabled()) {
      providerPath = myFileSpec + '/' + fileName;
      if (!provider.isApplicable(providerPath, myRoamingType)) {
        providerPath = null;
      }
    }
    else {
      providerPath = null;
    }

    // if another new scheme uses old name of this scheme, so, we must not delete it (as part of rename operation)
    boolean renamed = externalInfo != null && fileNameWithoutExtension != currentFileNameWithoutExtension && nameGenerator.value(currentFileNameWithoutExtension);
    if (providerPath == null) {
      VirtualFile file = null;
      VirtualFile dir = getDirectory();
      if (dir == null || !dir.isValid()) {
        dir = DirectoryBasedStorage.createDir(myIoDir, this);
        myDir = dir;
      }

      if (renamed) {
        file = dir.findChild(externalInfo.fileNameWithoutExtension + externalInfo.fileExtension);
        if (file != null) {
          AccessToken token = WriteAction.start();
          try {
            file.rename(this, fileName);
          }
          finally {
            token.finish();
          }
        }
      }

      if (file == null) {
        file = DirectoryBasedStorage.getFile(fileName, dir, this);
      }

      AccessToken token = WriteAction.start();
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
    else {
      if (renamed) {
        scheduleDelete(externalInfo);
      }
      provider.saveContent(providerPath, byteOut.getInternalBuffer(), byteOut.size(), myRoamingType);
    }

    if (externalInfo == null) {
      externalInfo = new ExternalInfo(fileNameWithoutExtension, mySchemeExtension);
      schemeToInfo.put(scheme, externalInfo);
    }
    else {
      externalInfo.setFileNameWithoutExtension(fileNameWithoutExtension, mySchemeExtension);
    }
    externalInfo.hash = newHash;
    externalInfo.schemeName = scheme.getName();
  }


  private void scheduleDelete(@NotNull ExternalInfo externalInfo) {
    filesToDelete.add(externalInfo.fileNameWithoutExtension + externalInfo.fileExtension);
  }

  private boolean isRenamed(@NotNull ExternalizableScheme scheme) {
    ExternalInfo info = schemeToInfo.get(scheme);
    return info != null && !scheme.getName().equals(info.schemeName);
  }

  @Nullable
  private List<Throwable> deleteFiles(@Nullable VirtualFile dir, List<Throwable> errors) {
    if (filesToDelete.isEmpty()) {
      return errors;
    }

    if (provider != null && provider.isEnabled()) {
      for (String name : filesToDelete) {
        try {
          StorageUtil.delete(provider, myFileSpec + '/' + name, myRoamingType);
        }
        catch (Throwable e) {
          if (errors == null) {
            errors = new SmartList<Throwable>();
          }
          errors.add(e);
        }
      }
    }
    else if (dir != null) {
      AccessToken token = null;
      try {
        for (VirtualFile file : dir.getChildren()) {
          if (filesToDelete.contains(file.getName())) {
            if (token == null) {
              token = WriteAction.start();
            }

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
      }
      finally {
        if (token != null) {
          token.finish();
        }
      }
    }

    filesToDelete.clear();
    return errors;
  }

  @Nullable
  private VirtualFile getDirectory() {
    VirtualFile result = myDir;
    if (result == null) {
      result = LocalFileSystem.getInstance().findFileByIoFile(myIoDir);
      myDir = result;
    }
    return result;
  }

  @Override
  public File getRootDirectory() {
    return myIoDir;
  }

  private void schemeAdded(@NotNull T scheme) {
    if (!(scheme instanceof ExternalizableScheme)) {
      return;
    }

    ExternalInfo info = schemeToInfo.get(scheme);
    if (info != null) {
      filesToDelete.remove(info.fileNameWithoutExtension + info.fileExtension);
    }
  }

  @Override
  public void setSchemes(@NotNull final List<T> newSchemes, @Nullable Condition<T> removeCondition) {
    if (removeCondition == null) {
      mySchemes.clear();
    }
    else {
      for (int i = mySchemes.size() - 1; i >= 0; i--) {
        T scheme = mySchemes.get(i);
        if (removeCondition.value(scheme)) {
          mySchemes.remove(i);
        }
      }
    }

    retainExternalInfo(newSchemes);

    mySchemes.ensureCapacity(newSchemes.size());
    for (T scheme : newSchemes) {
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
    if (schemeToInfo.isEmpty()) {
      return;
    }

    schemeToInfo.retainEntries(new TObjectObjectProcedure<ExternalizableScheme, ExternalInfo>() {
      @Override
      public boolean execute(ExternalizableScheme scheme, ExternalInfo info) {
        for (T t : schemes) {
          if (t == scheme) {
            return true;
          }
        }

        scheduleDelete(info);
        return false;
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
          ExternalInfo oldInfo = schemeToInfo.remove((ExternalizableScheme)existing);
          if (oldInfo != null && scheme instanceof ExternalizableScheme) {
            ExternalInfo newInfo = schemeToInfo.get((ExternalizableScheme)scheme);
            if (newInfo == null) {
              schemeToInfo.put((ExternalizableScheme)scheme, oldInfo);
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
    schemeToInfo.forEachValue(new TObjectProcedure<ExternalInfo>() {
      @Override
      public boolean execute(ExternalInfo info) {
        scheduleDelete(info);
        return true;
      }
    });

    myCurrentScheme = null;
    mySchemes.clear();
    schemeToInfo.clear();
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
    myCurrentScheme = null;
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
          ExternalInfo info = schemeToInfo.remove((ExternalizableScheme)s);
          if (info != null) {
            scheduleDelete(info);
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
    public ExternalInfo(@NotNull String fileNameWithoutExtension, @NotNull String fileExtension) {
      this.fileNameWithoutExtension = fileNameWithoutExtension;
      this.fileExtension = fileExtension;
    }

    // we keep it to detect rename
    private String schemeName;


    @NotNull
    private String fileNameWithoutExtension;

    private int hash;

    private String fileExtension;

    public void setFileNameWithoutExtension(@NotNull String nameWithoutExtension, @NotNull String extension) {
      fileNameWithoutExtension = nameWithoutExtension;
      fileExtension = extension;
    }

    @Override
    public String toString() {
      return fileNameWithoutExtension;
    }
  }

  @Override
  public String toString() {
    return myFileSpec;
  }
}
