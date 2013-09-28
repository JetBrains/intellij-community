/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.impl.stores.StorageUtil;
import com.intellij.openapi.components.impl.stores.StreamProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.DocumentRunnable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.UniqueFileNamesProvider;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.UniqueNameGenerator;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SchemesManagerImpl<T extends Scheme, E extends ExternalizableScheme> extends AbstractSchemesManager<T, E> {
  private static final Logger LOG = Logger.getInstance("#" + SchemesManagerFactoryImpl.class.getName());

  @NonNls private static final String DEFAULT_EXT = ".xml";

  private final Set<String> myDeletedNames = new LinkedHashSet<String>();
  private final Set<String> myFilesToDelete = new HashSet<String>();

  @NonNls private static final String SHARED_SCHEME = "shared-scheme";
  @NonNls private static final String SHARED_SCHEME_ORIGINAL = "shared-scheme-original";
  private static final String NAME = "name";
  @NonNls private static final String ORIGINAL_SCHEME_PATH = "original-scheme-path";
  private final String myFileSpec;
  private final SchemeProcessor<E> myProcessor;
  private final RoamingType myRoamingType;


  @NonNls private static final String SCHEME_LOCAL_COPY = "scheme-local-copy";
  @NonNls private static final String DELETED_XML = "__deleted.xml";
  private final StreamProvider myProvider;
  private final File myBaseDir;
  private VirtualFile myVFSBaseDir;
  private static final String DESCRIPTION = "description";
  private static final boolean EXPORT_IS_AVAILABLE = false;
  private static final String USER = "user";

  private boolean myListenerAdded = false;
  private Alarm myRefreshAlarm;
  
  private String mySchemeExtension = DEFAULT_EXT;
  private boolean myUpgradeExtension = false;

  public SchemesManagerImpl(@NotNull String fileSpec,
                            @NotNull SchemeProcessor<E> processor,
                            @NotNull RoamingType roamingType,
                            @Nullable StreamProvider provider,
                            @NotNull File baseDir) {

    myFileSpec = fileSpec;
    myProcessor = processor;
    myRoamingType = roamingType;
    myProvider = provider;
    myBaseDir = baseDir;
    if (processor instanceof SchemeExtensionProvider) {
      mySchemeExtension = ((SchemeExtensionProvider)processor).getSchemeExtension();
      myUpgradeExtension = ((SchemeExtensionProvider)processor).isUpgradeNeeded();
    }

    //noinspection ResultOfMethodCallIgnored
    myBaseDir.mkdirs();

    if (ApplicationManager.getApplication().isUnitTestMode() || !ApplicationManager.getApplication().isCommandLine()) {
      addVFSListener();
    }
  }

  @Override
  @NotNull
  public Collection<E> loadSchemes() {
    if (myVFSBaseDir != null) {
      return doLoad();
    }
    else {
      return Collections.emptyList();
    }

  }

  private Collection<E> doLoad() {
    myDeletedNames.addAll(readDeletedSchemeNames());


    Map<String, E> read = new LinkedHashMap<String, E>();

    for (E e : readSchemesFromFileSystem()) {
      read.put(e.getName(), e);
    }

    for (E e : readSchemesFromProviders()) {
      read.put(e.getName(), e);
    }

    Collection<E> result = read.values();
    initLoadedSchemes(result);

    return result;
  }

  private void addVFSListener() {
    Application app = ApplicationManager.getApplication();
    if (app == null || myListenerAdded) return;

    final LocalFileSystem system = LocalFileSystem.getInstance();
    myVFSBaseDir = system.findFileByIoFile(myBaseDir);

    if (myVFSBaseDir == null && !app.isUnitTestMode() && !app.isHeadlessEnvironment()) {
      myRefreshAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
      myRefreshAlarm.addRequest(new Runnable(){
        @Override
        public void run() {
          ensureVFSBaseDir();
        }
      }, 60 * 1000, ModalityState.NON_MODAL);
    }

    system.addVirtualFileListener(new VirtualFileAdapter() {
      @Override
      public void contentsChanged(final VirtualFileEvent event) {
        onFileContentChanged(event);
      }

      @Override
      public void fileCreated(final VirtualFileEvent event) {
        VirtualFile file = event.getFile();

        if (event.getRequestor() == null && isFileUnder(file, myVFSBaseDir) && !myInsideSave) {
          ArrayList<E> read = new ArrayList<E>();
          readSchemeFromFile(read, file, true);
          if (!read.isEmpty()) {
            E readScheme = read.get(0);
            myProcessor.initScheme(readScheme);
            myProcessor.onSchemeAdded(readScheme);
          }
        }

      }

      @Override
      public void fileDeleted(final VirtualFileEvent event) {
        VirtualFile parent = event.getParent();

        if (event.getRequestor() == null && parent != null && parent.equals(myVFSBaseDir) && !myInsideSave) {
          File ioFile = new File(event.getFileName());
          E scheme = findSchemeFor(ioFile.getName());
          T oldCurrentScheme = null;
          if (scheme != null) {
            oldCurrentScheme = getCurrentScheme();
            @SuppressWarnings("unchecked") T t = (T)scheme;
            removeScheme(t);
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

    });

    myListenerAdded = true;
  }

  private void onFileContentChanged(final VirtualFileEvent event) {
    VirtualFile file = event.getFile();

    if (event.getRequestor() == null && isFileUnder(file, myVFSBaseDir) && !myInsideSave) {
      File ioFile = new File(file.getPath());
      E scheme = findSchemeFor(ioFile.getName());
      ArrayList<E> read = new ArrayList<E>();
      T oldCurrentScheme = null;
      if (scheme != null) {
        oldCurrentScheme = getCurrentScheme();
        @SuppressWarnings("unchecked") T t = (T)scheme;
        removeScheme(t);
        myProcessor.onSchemeDeleted(scheme);
      }

      readSchemeFromFile(read, file, true);
      if (!read.isEmpty()) {
        E readScheme = read.get(0);
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

  private static boolean isFileUnder(final VirtualFile file, final VirtualFile baseDirFile) {
    return file.getParent().equals(baseDirFile);
  }

  private Collection<String> readDeletedSchemeNames() {
    Collection<String> result = new THashSet<String>();
    if (myProvider == null || !myProvider.isEnabled()) {
      return result;
    }

    try {
      Document deletedNameDoc = StorageUtil.loadDocument(myProvider.loadContent(getFileFullPath(DELETED_XML), myRoamingType));
      if (deletedNameDoc != null) {
        for (Element child : deletedNameDoc.getRootElement().getChildren()) {
          String deletedSchemeName = child.getAttributeValue("name");
          if (deletedSchemeName != null) {
            result.add(deletedSchemeName);
          }
        }
      }
    }
    catch (Exception e) {
      LOG.debug(e);
    }

    return result;
  }

  private void initLoadedSchemes(final Collection<E> read) {
    for (E scheme : read) {
      myProcessor.initScheme(scheme);
      checkCurrentScheme(scheme);
    }
  }

  private Collection<E> readSchemesFromProviders() {
    Collection<E> result = new ArrayList<E>();
    if (myProvider == null || !myProvider.isEnabled()) {
      return result;
    }

    for (String subPath : myProvider.listSubFiles(myFileSpec, myRoamingType)) {
      if (!subPath.equals(DELETED_XML)) {
        try {
          final Document subDocument = StorageUtil.loadDocument(myProvider.loadContent(getFileFullPath(subPath), myRoamingType));
          if (subDocument != null) {
            E scheme = readScheme(subDocument);
            boolean fileRenamed = false;
            T existing = findSchemeByName(scheme.getName());
            if (existing != null && existing instanceof ExternalizableScheme) {
              String currentFileName = ((ExternalizableScheme)existing).getExternalInfo().getCurrentFileName();
              if (currentFileName != null && !currentFileName.equals(subPath)) {
                deleteServerFiles(subPath);
                subPath = currentFileName;
                fileRenamed = true;
              }
            }
            String fileName = checkFileNameIsFree(subPath, scheme.getName());

            if (!fileRenamed && !fileName.equals(subPath)) {
              deleteServerFiles(subPath);
            }

            if (scheme != null) {
              loadScheme(scheme, false, fileName);
              result.add(scheme);
            }
          }
        }
        catch (Exception e) {
          LOG.info("Cannot load data from IDEAServer: " + e.getLocalizedMessage());
        }
      }
    }

    return result;
  }

  private VirtualFile ensureFileText(final String fileName, final byte[] text) throws IOException {
    final IOException[] ex = new IOException[] {null};
    final VirtualFile _file = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        VirtualFile file = myVFSBaseDir.findChild(fileName);
        try {
          if (file == null) file = myVFSBaseDir.createChildData(SchemesManagerImpl.this, fileName);
          if (!Arrays.equals(file.contentsToByteArray(), text)) {
            file.setBinaryContent(text);
          }
        }
        catch (IOException e) {
          ex[0] = e;
        }

        return file;
      }
    });

    if (ex[0] != null) throw ex[0];
    return _file;
  }

  private String checkFileNameIsFree(final String subPath, final String schemeName) {
    for (Scheme scheme : mySchemes) {
      if (scheme instanceof ExternalizableScheme) {
        ExternalInfo externalInfo = ((ExternalizableScheme)scheme).getExternalInfo();
        String name = externalInfo.getCurrentFileName();
        if (name != null) {
          String fileName = name + mySchemeExtension;
          if (fileName.equals(subPath) && !Comparing.equal(schemeName, scheme.getName())) {
            return createUniqueFileName(collectAllFileNames(), UniqueFileNamesProvider.convertName(schemeName));
            /*VirtualFile oldFile = myVFSBaseDir.findChild(subPath);
            if (oldFile != null) {
              oldFile.copy(this, myVFSBaseDir, uniqueFileName + EXT);
            }
            externalInfo.setCurrentFileName(uniqueFileName);*/
          }
        }
      }
    }

    return subPath;
  }

  private Collection<String> collectAllFileNames() {
    HashSet<String> result = new HashSet<String>();
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

  private static String createUniqueFileName(final Collection<String> strings, final String schemeName) {
    return UniqueNameGenerator.generateUniqueName(schemeName, strings);
  }

  private void loadScheme(final E scheme, boolean forceAdd, final String name) {
    if (scheme != null && (!myDeletedNames.contains(scheme.getName()) || forceAdd)) {
      T existing = findSchemeByName(scheme.getName());
      if (existing != null) {
        if (!Comparing.equal(existing.getClass(), scheme.getClass())) {
          LOG.warn("'" + scheme.getName() + "' " + existing.getClass().getSimpleName() + " replaced with " + scheme.getClass().getSimpleName());
        }

        mySchemes.remove(existing);

        if (isExternalizable(existing)) {
          @SuppressWarnings("unchecked") E e = (E)existing;
          myProcessor.onSchemeDeleted(e);
        }

      }
      @SuppressWarnings("unchecked") T t = (T)scheme;
      addNewScheme(t, true);
      saveFileName(name, scheme);
      scheme.getExternalInfo().setPreviouslySavedName(scheme.getName());
    }

  }

  private Collection<E> readSchemesFromFileSystem() {
    Collection<E> result = new ArrayList<E>();
    VirtualFile[] files = myVFSBaseDir.getChildren();
    if (files != null) {
      for (VirtualFile file : files) {
        readSchemeFromFile(result, file, false);
      }
    }
    else {
      ApplicationManager.getApplication().invokeLater(
          new Runnable(){
            @Override
            public void run() {
              String msg = "Cannot read directory: " + myBaseDir.getAbsolutePath() + " directory does not exist";
              Messages.showErrorDialog(msg, "Read Settings");
            }
          }
      );
    }
    return result;
  }


  private boolean canRead(VirtualFile file) {
    if (!file.isDirectory()) {
      String ext = "." + file.getExtension();
      if (DEFAULT_EXT.equalsIgnoreCase(ext) && !DEFAULT_EXT.equals(mySchemeExtension) && myUpgradeExtension) {
        return myVFSBaseDir.findChild(file.getName() + mySchemeExtension) == null;
      }
      else if (mySchemeExtension.equalsIgnoreCase(ext)) {
        return true;
      }
    }
    return false;
  }

  private void readSchemeFromFile(final Collection<E> result, final VirtualFile file, final boolean forceAdd) {
    if (canRead(file)) {
      try {
        final Document document;
        try {
          document = JDOMUtil.loadDocument(file.getInputStream());
        }
        catch (JDOMException e) {
          try {
            File initialIOFile = new File(myBaseDir, file.getName());
            if (initialIOFile.isFile()) {
              FileUtil.copy(initialIOFile, new File(myBaseDir, file.getName() + ".copy"));
            }
          }
          catch (IOException e1) {
            LOG.info(e1);
            //ignore
          }
          LOG.info("Error reading file " + file.getPath() + ": " + e.getLocalizedMessage());
          throw e;
        }
        final E scheme = readScheme(document);
        if (scheme != null) {
          if (scheme.getName() == null) {
            String suggestedName = FileUtil.getNameWithoutExtension(file.getName());
            if (!"_".equals(suggestedName)) {
              scheme.setName(suggestedName);
            }
          }

          loadScheme(scheme, forceAdd, file.getName());
          result.add(scheme);
        }
      }
      catch (final Exception e) {
        ApplicationManager.getApplication().invokeLater(
            new Runnable(){
              @Override
              public void run() {
                String msg = "Cannot read scheme " + file.getName() + "  from '" + myFileSpec + "': " + e.getLocalizedMessage();
                LOG.info(msg, e);
                Messages.showErrorDialog(msg, "Load Settings");
              }
            }
        );

      }
    }
  }

  @Nullable
  private E readScheme(final Document subDocument) throws InvalidDataException, IOException, JDOMException {
    Element rootElement = subDocument.getRootElement();
    if (rootElement.getName().equals(SHARED_SCHEME)) {
      String schemeName = rootElement.getAttributeValue(NAME);
      String schemePath = rootElement.getAttributeValue(ORIGINAL_SCHEME_PATH);

      Document sharedDocument = loadGlobalScheme(schemePath);

      if (sharedDocument != null) {
        E result = readScheme(sharedDocument);
        if (result != null) {
          renameScheme(result, schemeName);
          result.getExternalInfo().setOriginalPath(schemePath);
          result.getExternalInfo().setIsImported(true);
        }
        return result;
      }
      else {
        Element localCopyElement = subDocument.getRootElement().getChild(SCHEME_LOCAL_COPY);
        if (localCopyElement != null) {
          Element firstChild = localCopyElement.getChildren().get(0);
          return myProcessor.readScheme(new Document(firstChild.clone()));
        }
        else {
          return null;
        }
      }
    }
    else if (rootElement.getName().equals(SHARED_SCHEME_ORIGINAL)) {
      SharedSchemeData schemeData = unwrap(subDocument);
      E scheme = myProcessor.readScheme(schemeData.original);
      if (scheme != null) {
        renameScheme(scheme, schemeData.name);
      }
      return scheme;
    }
    else {
      return myProcessor.readScheme(subDocument);
    }

  }

  @Nullable
  private static Document loadGlobalScheme(final String schemePath) throws IOException {
    StreamProvider provider = getProvider();
    return provider != null && provider.isEnabled() ? StorageUtil.loadDocument(provider.loadContent(schemePath, RoamingType.GLOBAL)) : null;
  }

  private void saveFileName(String fileName, final E schemeKey) {
    if (StringUtil.endsWithIgnoreCase(fileName, mySchemeExtension)) {
      fileName = fileName.substring(0, fileName.length() - mySchemeExtension.length());
    }
    else if (StringUtil.endsWithIgnoreCase(fileName, DEFAULT_EXT)) {
      fileName = fileName.substring(0, fileName.length() - DEFAULT_EXT.length());
    }
    schemeKey.getExternalInfo().setCurrentFileName(fileName);
  }

  private static long computeHashValue(final Document document) {
    return JDOMUtil.getTreeHash(document);
  }

  @Nullable
  private Document writeSchemeToDocument(final E scheme) throws WriteExternalException {
    if (isShared(scheme)) {
      String originalPath = scheme.getExternalInfo().getOriginalPath();
      if (originalPath != null) {
        Element root = new Element(SHARED_SCHEME);
        root.setAttribute(NAME, scheme.getName());
        root.setAttribute(ORIGINAL_SCHEME_PATH, originalPath);

        Element localCopy = new Element(SCHEME_LOCAL_COPY);
        localCopy.addContent(myProcessor.writeScheme(scheme).getRootElement().clone());

        root.addContent(localCopy);

        return new Document(root);
      }
      else {
        return null;
      }
    }
    else {
      return myProcessor.writeScheme(scheme);
    }
  }

  public void updateConfigFilesFromStreamProviders() {

  }

  class SharedSchemeData {
    Document original;
    String name;
    String user;
    String description;
    E scheme;
  }

  @Override
  @NotNull
  public Collection<SharedScheme<E>> loadSharedSchemes(Collection<T> currentSchemeList) {
    StreamProvider provider = getProvider();
    if (provider == null || !provider.isEnabled()) {
      return Collections.emptyList();
    }

    Collection<String> names = new THashSet<String>(getAllSchemeNames(currentSchemeList));
    Map<String, SharedScheme<E>> result = new THashMap<String, SharedScheme<E>>();
    for (String subPath : provider.listSubFiles(myFileSpec, RoamingType.GLOBAL)) {
      try {
        final Document subDocument = StorageUtil.loadDocument(provider.loadContent(getFileFullPath(subPath), RoamingType.GLOBAL));
        if (subDocument != null) {
          SharedSchemeData original = unwrap(subDocument);
          final E scheme = myProcessor.readScheme(original.original);
          if (!alreadyShared(subPath, currentSchemeList)) {
            String schemeName = original.name;
            String uniqueName = UniqueNameGenerator.generateUniqueName("[shared] " + schemeName, names);
            renameScheme(scheme, uniqueName);
            schemeName = uniqueName;
            scheme.getExternalInfo().setOriginalPath(getFileFullPath(subPath));
            scheme.getExternalInfo().setIsImported(true);
            result.put(schemeName, new SharedScheme<E>(original.user == null ? "unknown" : original.user, original.description, scheme));
          }
        }
      }
      catch (Exception e) {
        LOG.debug("Cannot load data from IDEAServer: " + e.getLocalizedMessage());
      }
    }

    for (SharedScheme<E> t : result.values()) {
      myProcessor.initScheme(t.getScheme());
    }

    return result.values();
  }

  private SharedSchemeData unwrap(final Document subDocument) {
    SharedSchemeData result = new SharedSchemeData();
    Element rootElement = subDocument.getRootElement();
    if (rootElement.getName().equals(SHARED_SCHEME_ORIGINAL)) {
      result.name = rootElement.getAttributeValue(NAME);
      result.description = rootElement.getAttributeValue(DESCRIPTION);
      result.user = rootElement.getAttributeValue(USER);
      result.original = new Document(rootElement.getChildren().iterator().next().clone());
    }
    else {
      result.name = rootElement.getAttributeValue(NAME);
      result.original = subDocument;
    }
    return result;
  }

  private boolean alreadyShared(final String subPath, final Collection<T> currentSchemeList) {
    for (T t : currentSchemeList) {
      if (t instanceof ExternalizableScheme) {
        ExternalInfo info = ((ExternalizableScheme)t).getExternalInfo();
        if (info.isIsImported()) {
          if (getFileFullPath(subPath).equals(info.getOriginalPath())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private String getFileFullPath(final String subPath) {
    return myFileSpec + "/" + subPath;
  }

  @Override
  public void exportScheme(final E scheme, final String name, final String description) throws WriteExternalException, IOException {
    StreamProvider provider = getProvider();
    if (provider == null) {
      return;
    }

    Document document = myProcessor.writeScheme(scheme);
    if (document != null) {
      String fileSpec = getFileFullPath(UniqueFileNamesProvider.convertName(scheme.getName())) + mySchemeExtension;
      if (!provider.isApplicable(fileSpec, RoamingType.GLOBAL)) {
        return;
      }

      Document wrapped = wrap(document, name, description);
      if (provider instanceof CurrentUserHolder) {
        wrapped = wrapped.clone();
        String userName = ((CurrentUserHolder)provider).getCurrentUserName();
        if (userName != null) {
          wrapped.getRootElement().setAttribute(USER, userName);
        }
      }
      StorageUtil.doSendContent(provider, fileSpec, wrapped, RoamingType.GLOBAL, false);
    }
  }

  private static Document wrap(final Document original, final String name, final String description) {
    Element sharedElement = new Element(SHARED_SCHEME_ORIGINAL);
    sharedElement.setAttribute(NAME, name);
    sharedElement.setAttribute(DESCRIPTION, description);
    sharedElement.addContent(original.getRootElement().clone());
    return new Document(sharedElement);
  }

  @Override
  public boolean isImportAvailable() {
    return getProvider() != null;
  }

  @Nullable
  private static StreamProvider getProvider() {
    StreamProvider provider = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager().getStreamProvider();
    return provider == null || !provider.isEnabled() ? null : provider;
  }

  @Override
  public boolean isExportAvailable() {
    return EXPORT_IS_AVAILABLE;
  }

  @Override
  public boolean isShared(final Scheme scheme) {
    return scheme instanceof ExternalizableScheme && ((ExternalizableScheme)scheme).getExternalInfo().isIsImported();
  }

  @Override
  public void save() throws WriteExternalException {

    if (myRefreshAlarm != null) {
      myRefreshAlarm.cancelAllRequests();
      myRefreshAlarm = null;
    }

    if (myVFSBaseDir == null) {
      ensureVFSBaseDir();
    }

    if (myVFSBaseDir != null) {
      doSave();
    }
  }

  private void ensureVFSBaseDir() {
    //noinspection ResultOfMethodCallIgnored
    myBaseDir.mkdirs();
    ApplicationManager.getApplication().runWriteAction(new DocumentRunnable.IgnoreDocumentRunnable(){
      @Override
      public void run() {
        VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myBaseDir);
        myVFSBaseDir = dir;
        if (dir != null) {
          dir.getChildren();
          ((NewVirtualFile)dir).markDirtyRecursively();
          dir.refresh(false, true);
        }
      }
    });
  }

  private boolean myInsideSave = false;

  private void doSave() throws WriteExternalException {
    myInsideSave = true;
    try {
      ApplicationManager.getApplication().runWriteAction(new DocumentRunnable.IgnoreDocumentRunnable()  {
        @Override
        public void run() {
          ((NewVirtualFile)myVFSBaseDir).markDirtyRecursively();
          myVFSBaseDir.refresh(false, true);
        }
      });

      final Collection<T> schemes = getAllSchemes();
      //noinspection ResultOfMethodCallIgnored
      myBaseDir.mkdirs();

      final UniqueFileNamesProvider fileNameProvider = new UniqueFileNamesProvider();
      reserveUsingFileNames(schemes, fileNameProvider);

      final WriteExternalException[] ex = new WriteExternalException[1];
      ApplicationManager.getApplication().runWriteAction(new DocumentRunnable.IgnoreDocumentRunnable() {
        @Override
        public void run() {
          deleteFilesFromDeletedSchemes();
          try {
            saveSchemes(schemes, fileNameProvider);
          }
          catch (WriteExternalException e) {
            ex[0] = e;
          }
        }
      });
      if (ex[0] != null) {
        throw ex[0];
      }

      if (myDeletedNames.isEmpty()) {
        deleteServerFiles(DELETED_XML);
      }
      else if (myProvider != null && myProvider.isEnabled()) {
        StorageUtil.sendContent(myProvider, getFileFullPath(DELETED_XML), createDeletedDocument(), myRoamingType, true);
      }
    }
    finally {
      myInsideSave = false;
    }
  }

  @Override
  public File getRootDirectory() {
    return myBaseDir;
  }

  private void deleteFilesFromDeletedSchemes() {
    for (String deletedName : myFilesToDelete) {
      deleteLocalAndServerFiles(deletedName + mySchemeExtension);
      if (!DEFAULT_EXT.equals(mySchemeExtension))  {
        deleteLocalAndServerFiles(deletedName + DEFAULT_EXT);
      }
    }
    myFilesToDelete.clear();
  }

  private void deleteLocalAndServerFiles(final String fileName) {
    VirtualFile file = myVFSBaseDir.findChild(fileName);
    if (file != null) {
      try {
        file.delete(this);
      }
      catch (IOException e) {
        LOG.info("Cannot delete file " + file.getPath() + ": " + e.getLocalizedMessage());
      }
    }
    deleteServerFiles(fileName);
  }

  private void deleteServerFiles(final String fileName) {
    if (myProvider != null && myProvider.isEnabled()) {
      StorageUtil.deleteContent(myProvider, getFileFullPath(fileName), myRoamingType);
    }
  }

  private void saveSchemes(final Collection<T> schemes, final UniqueFileNamesProvider fileNameProvider) throws WriteExternalException {
    for (T scheme : schemes) {
      if (isExternalizable(scheme)) {
        @SuppressWarnings("unchecked") final E eScheme = (E)scheme;
        eScheme.getExternalInfo().setPreviouslySavedName(eScheme.getName());
        if (myProcessor.shouldBeSaved(eScheme)) {
          final String fileName = getFileNameForScheme(fileNameProvider, eScheme);
          try {

            final Document document = writeSchemeToDocument(eScheme);
            if (document != null) {
              long newHash = computeHashValue(document);
              Long oldHash = eScheme.getExternalInfo().getHash();
              saveIfNeeded(eScheme, fileName, document, newHash, oldHash);
            }
          }
          catch (final IOException e) {
            Application app = ApplicationManager.getApplication();
            if (app.isUnitTestMode() || app.isCommandLine()) {
              LOG.error("Cannot write scheme " + fileName + " in '" + myFileSpec + "': " + e.getLocalizedMessage(), e);
            }
            else {
              app.invokeLater(new Runnable(){
                @Override
                public void run() {
                  Messages.showErrorDialog("Cannot save scheme '" + eScheme.getName() + ": " + e.getLocalizedMessage(), "Save Settings");
                }
              });
            }
          }
        }
      }
    }
  }

  private String getFileNameForScheme(final UniqueFileNamesProvider fileNameProvider, final E scheme) {
    final String fileName;
    if (scheme.getExternalInfo().getCurrentFileName() != null) {
      fileName = scheme.getExternalInfo().getCurrentFileName();
      fileNameProvider.reserveFileName(fileName);
    }
    else {
      fileName = fileNameProvider.suggestName(scheme.getName());
    }

    return fileName + mySchemeExtension;
  }

  private void saveIfNeeded(E schemeKey, String fileName, Document document, long newHash, Long oldHash) throws IOException {
    if (oldHash == null || newHash != oldHash.longValue() || myVFSBaseDir.findChild(fileName) == null) {
      ensureFileText(fileName, StorageUtil.documentToBytes(document, true).toByteArray());
      schemeKey.getExternalInfo().setHash(newHash);
      saveFileName(fileName, schemeKey);
      saveOnServer(fileName, document);
    }
  }

  private void saveOnServer(final String fileName, final Document document) {
    if (myProvider != null && myProvider.isEnabled()) {
      StorageUtil.sendContent(myProvider, getFileFullPath(fileName), document, myRoamingType, true);
    }
  }

  private void reserveUsingFileNames(final Collection<T> schemes, final UniqueFileNamesProvider fileNameProvider) {
    fileNameProvider.reserveFileName(DELETED_XML);

    for (T scheme : schemes) {
      if (scheme instanceof ExternalizableScheme) {
        ExternalInfo info = ((ExternalizableScheme)scheme).getExternalInfo();
        final String fileName = info.getCurrentFileName();
        if (fileName != null) {
          if (Comparing.equal(info.getPreviouslySavedName(),scheme.getName())) {
            fileNameProvider.reserveFileName(fileName);
          }
          else {
            myFilesToDelete.add(fileName);
            info.setCurrentFileName(null);
          }
        }

      }
    }
  }

  private Document createDeletedDocument() {
    Element root = new Element("deleted-schemes");
    Document result = new Document(root);
    for (String deletedName : myDeletedNames) {
      Element child = new Element("scheme");
      root.addContent(child);
      child.setAttribute("name", deletedName);
    }

    return result;
  }

  @Override
  protected void onSchemeDeleted(final Scheme toDelete) {
    if (toDelete instanceof ExternalizableScheme) {
      ExternalInfo info = ((ExternalizableScheme)toDelete).getExternalInfo();
      String previouslyUsedName = info.getPreviouslySavedName();

      if (previouslyUsedName != null) {
        myDeletedNames.add(previouslyUsedName);
      }

      if (info.getCurrentFileName() != null) {
        myFilesToDelete.add(info.getCurrentFileName());
      }
    }

  }

  @Override
  protected void onSchemeAdded(final T scheme) {
    myDeletedNames.remove(scheme.getName());
    if (scheme instanceof ExternalizableScheme) {
      ((ExternalizableScheme)scheme).getExternalInfo().setPreviouslySavedName(scheme.getName());
    }
  }
}
