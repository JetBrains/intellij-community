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
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.ide.highlighter.custom.impl.ReadFileType;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.*;
import com.intellij.openapi.options.BaseSchemeProcessor;
import com.intellij.openapi.options.ExternalInfo;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.FileSystemInterface;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.Processor;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Yura Cangea
 */
public class FileTypeManagerImpl extends FileTypeManagerEx implements NamedJDOMExternalizable, ExportableApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl");
  private static final int VERSION = 11;
  private static final Key<FileType> FILE_TYPE_KEY = Key.create("FILE_TYPE_KEY");
  private static final Key<FileType> DETECTED_FROM_CONTENT_FILE_TYPE_KEY = Key.create("DETECTED_FROM_CONTENT_FILE_TYPE_KEY");

  private final Set<FileType> myDefaultTypes = new THashSet<FileType>();
  private final List<FileTypeIdentifiableByVirtualFile> mySpecialFileTypes = new ArrayList<FileTypeIdentifiableByVirtualFile>();

  private FileTypeAssocTable<FileType> myPatternsTable = new FileTypeAssocTable<FileType>();
  private final IgnoredPatternSet myIgnoredPatterns = new IgnoredPatternSet();
  private final IgnoredFileCache myIgnoredFileCache = new IgnoredFileCache(myIgnoredPatterns);

  private final FileTypeAssocTable<FileType> myInitialAssociations = new FileTypeAssocTable<FileType>();
  private final Map<FileNameMatcher, String> myUnresolvedMappings = new THashMap<FileNameMatcher, String>();
  private final Map<FileNameMatcher, Trinity<String, String, Boolean>> myUnresolvedRemovedMappings = new THashMap<FileNameMatcher, Trinity<String, String, Boolean>>();
  /** This will contain removed mappings with "approved" states */
  private final Map<FileNameMatcher, Pair<FileType, Boolean>> myRemovedMappings = new THashMap<FileNameMatcher, Pair<FileType, Boolean>>();

  @NonNls private static final String ELEMENT_FILETYPE = "filetype";
  @NonNls private static final String ELEMENT_FILETYPES = "filetypes";
  @NonNls private static final String ELEMENT_IGNOREFILES = "ignoreFiles";
  @NonNls private static final String ATTRIBUTE_LIST = "list";

  @NonNls private static final String ATTRIBUTE_VERSION = "version";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String ATTRIBUTE_DESCRIPTION = "description";
  @NonNls private static final String ATTRIBUTE_ICON = "icon";
  @NonNls private static final String ATTRIBUTE_EXTENSIONS = "extensions";
  @NonNls private static final String ATTRIBUTE_BINARY = "binary";
  @NonNls private static final String ATTRIBUTE_DEFAULT_EXTENSION = "default_extension";

  private static class StandardFileType {
    private final FileType fileType;
    private final List<FileNameMatcher> matchers;

    private StandardFileType(final FileType fileType, final List<FileNameMatcher> matchers) {
      this.fileType = fileType;
      this.matchers = matchers;
    }
  }

  private final MessageBus myMessageBus;
  private final Map<String, StandardFileType> myStandardFileTypes = new LinkedHashMap<String, StandardFileType>();
  @NonNls private static final String[] FILE_TYPES_WITH_PREDEFINED_EXTENSIONS = {"JSP", "JSPX", "DTD", "HTML", "Properties", "XHTML"};
  private final SchemesManager<FileType, AbstractFileType> mySchemesManager;
  @NonNls private static final String FILE_SPEC = "$ROOT_CONFIG$/filetypes";

  private void initStandardFileTypes() {
    final FileTypeConsumer consumer = new FileTypeConsumer() {
      @Override
      public void consume(@NotNull FileType fileType) {
        register(fileType, parse(fileType.getDefaultExtension()));
      }

      @Override
      public void consume(@NotNull final FileType fileType, final String extensions) {
        register(fileType, parse(extensions));
      }

      @Override
      public void consume(@NotNull final FileType fileType, final FileNameMatcher... matchers) {
        register(fileType, new ArrayList<FileNameMatcher>(Arrays.asList(matchers)));
      }

      @Override
      public FileType getStandardFileTypeByName(@NotNull final String name) {
        final StandardFileType type = myStandardFileTypes.get(name);
        return type != null ? type.fileType : null;
      }

      private void register(final FileType fileType, final List<FileNameMatcher> fileNameMatchers) {
        final StandardFileType type = myStandardFileTypes.get(fileType.getName());
        if (type != null) {
          for (FileNameMatcher matcher : fileNameMatchers) type.matchers.add(matcher);
        }
        else {
          myStandardFileTypes.put(fileType.getName(), new StandardFileType(fileType, fileNameMatchers));
        }
      }
    };

    for (FileTypeFactory factory : Extensions.getExtensions(FileTypeFactory.FILE_TYPE_FACTORY_EP)) {
      try {
        factory.createFileTypes(consumer);
      }
      catch (Throwable t) {
        PluginManager.handleComponentError(t, factory.getClass().getName(), null);
      }
    }
  }

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  public FileTypeManagerImpl(MessageBus bus, SchemesManagerFactory schemesManagerFactory) {
    myMessageBus = bus;
    mySchemesManager = schemesManagerFactory.createSchemesManager(FILE_SPEC, new BaseSchemeProcessor<AbstractFileType>() {
      @Override
      public AbstractFileType readScheme(final Document document) throws InvalidDataException {
        if (document == null) {
          throw new InvalidDataException();
        }
        Element root = document.getRootElement();
        if (root == null || !ELEMENT_FILETYPE.equals(root.getName())) {
          throw new InvalidDataException();
        }
        Element element = root.getChild(AbstractFileType.ELEMENT_HIGHLIGHTING);
        if (element != null) {
          final SyntaxTable table = AbstractFileType.readSyntaxTable(element);
          if (table != null) {
            ReadFileType type = new ReadFileType(table, root);
            String fileTypeName = root.getAttributeValue(ATTRIBUTE_NAME);
            String fileTypeDescr = root.getAttributeValue(ATTRIBUTE_DESCRIPTION);
            String iconPath = root.getAttributeValue(ATTRIBUTE_ICON);

            setFileTypeAttributes(fileTypeName, fileTypeDescr, iconPath, type);

            return type;
          }
        }

        return null;
      }

      @Override
      public boolean shouldBeSaved(final AbstractFileType fileType) {
        return shouldBeSavedToFile(fileType);
      }

      @Override
      public Document writeScheme(final AbstractFileType fileType) throws WriteExternalException {
        Element root = new Element(ELEMENT_FILETYPE);

        writeHeader(root, fileType);

        fileType.writeExternal(root);

        Element map = new Element(AbstractFileType.ELEMENT_EXTENSIONMAP);
        root.addContent(map);

        if (fileType instanceof ImportedFileType) {
          writeImportedExtensionsMap(map, (ImportedFileType)fileType);
        }
        else {
          writeExtensionsMap(map, fileType, false);
        }

        return new Document(root);
      }

      @Override
      public void onSchemeAdded(final AbstractFileType scheme) {
        fireBeforeFileTypesChanged();
        if (scheme instanceof ReadFileType) {
          loadFileType((ReadFileType)scheme);
        }
        fireFileTypesChanged();
      }

      @Override
      public void onSchemeDeleted(final AbstractFileType scheme) {
        fireBeforeFileTypesChanged();
        myPatternsTable.removeAllAssociations(scheme);
        fireFileTypesChanged();
      }
    }, RoamingType.PER_USER);
  }

  private static void writeImportedExtensionsMap(final Element map, final ImportedFileType type) {
    for (FileNameMatcher matcher : type.getOriginalPatterns()) {
      Element content = AbstractFileType.writeMapping(type, matcher, false);
      if (content != null) {
        map.addContent(content);
      }
    }
  }

  private boolean shouldBeSavedToFile(final FileType fileType) {
    if (!(fileType instanceof JDOMExternalizable) || !shouldSave(fileType)) return false;
    if (myDefaultTypes.contains(fileType) && !isDefaultModified(fileType)) return false;
    return true;
  }

  @Override
  @NotNull
  public FileType getStdFileType(@NotNull @NonNls String name) {
    StandardFileType stdFileType = myStandardFileTypes.get(name);
    return stdFileType != null ? stdFileType.fileType : PlainTextFileType.INSTANCE;
  }

  @Override
  @NotNull
  public File[] getExportFiles() {
    return new File[]{getOrCreateFileTypesDir(), PathManager.getOptionsFile(this)};
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return FileTypesBundle.message("filetype.settings.component");
  }
  // -------------------------------------------------------------------------
  // ApplicationComponent interface implementation
  // -------------------------------------------------------------------------

  @Override
  public void disposeComponent() {
  }

  @Override
  public void initComponent() {
    initStandardFileTypes();

    for (final StandardFileType pair : myStandardFileTypes.values()) {
      registerFileTypeWithoutNotification(pair.fileType, pair.matchers);
    }
    for (StandardFileType pair : myStandardFileTypes.values()) {
      registerReDetectedMappings(pair);
    }
    // Resolve unresolved mappings initialized before certain plugin initialized.
    for (final StandardFileType pair : myStandardFileTypes.values()) {
      bindUnresolvedMappings(pair.fileType);
    }
    if (loadAllFileTypes()) {
      restoreStandardFileExtensions();
    }
  }

  // -------------------------------------------------------------------------
  // Implementation of abstract methods
  // -------------------------------------------------------------------------

  @Override
  @NotNull
  public FileType getFileTypeByFileName(@NotNull String fileName) {
    FileType type = myPatternsTable.findAssociatedFileType(fileName);
    return type == null ? UnknownFileType.INSTANCE : type;
  }

  public static void cacheFileType(@NotNull VirtualFile file, @Nullable FileType fileType) {
    file.putUserData(FILE_TYPE_KEY, fileType);
  }

  @Override
  @NotNull
  public FileType getFileTypeByFile(@NotNull VirtualFile file) {
    FileType fileType = file.getUserData(FILE_TYPE_KEY);
    if (fileType != null) return fileType;

    if (file instanceof LightVirtualFile) {
      fileType = ((LightVirtualFile)file).getAssignedFileType();
      if (fileType != null) return fileType;
    }

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, size = mySpecialFileTypes.size(); i < size; i++) {
      FileTypeIdentifiableByVirtualFile type = mySpecialFileTypes.get(i);
      if (type.isMyFileType(file)) {
        return type;
      }
    }

    fileType = getFileTypeByFileName(file.getName());
    if (fileType != UnknownFileType.INSTANCE) return fileType;

    fileType = file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY);
    if (fileType != null) return fileType;

    return UnknownFileType.INSTANCE;
  }

  @NotNull
  @Override
  public FileType detectFileTypeFromContent(@NotNull VirtualFile file) {
    if (file.isDirectory() || !file.isValid() || file.is(VFileProperty.SPECIAL)) {
      return UnknownFileType.INSTANCE;
    }
    FileType fileType = file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY);
    if (fileType == null) {
      fileType = detectFromContent(file);
      file.putUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY, fileType);
    }
    return fileType;
  }

  @Override
  public FileType findFileTypeByName(String fileTypeName) {
    FileType type = getStdFileType(fileTypeName);
    // TODO: Abstract file types are not std one, so need to be restored specially,
    // currently there are 6 of them and restoration does not happen very often so just iteration is enough
    if (type == PlainTextFileType.INSTANCE && !fileTypeName.equals(type.getName())) {
      for (FileType fileType: getRegisteredFileTypes()) {
        if (fileTypeName.equals(fileType.getName())) {
          return fileType;
        }
      }
    }
    return type;
  }

  private static final AtomicInteger DETECTED_COUNT = new AtomicInteger();
  private static final int DETECT_BUFFER_SIZE = 8192;

  @NotNull
  private static FileType detectFromContent(@NotNull final VirtualFile file) {
    try {
      final long length = file.getLength();
      if (length == 0) return UnknownFileType.INSTANCE;

      final VirtualFileSystem fileSystem = file.getFileSystem();
      if (!(fileSystem instanceof FileSystemInterface)) return UnknownFileType.INSTANCE;

      if (SingleRootFileViewProvider.isTooLargeForContentLoading(file)) {
        return UnknownFileType.INSTANCE;
      }

      final InputStream inputStream = ((FileSystemInterface)fileSystem).getInputStream(file);
      final Ref<FileType> result;
      try {
        result = new Ref<FileType>(UnknownFileType.INSTANCE);
        FileUtil.processFirstBytes(inputStream, DETECT_BUFFER_SIZE, new Processor<ByteSequence>() {
          @Override
          public boolean process(ByteSequence byteSequence) {
            boolean isText = guessIfText(file, byteSequence);
            CharSequence text;
            if (isText) {
              byte[] bytes = Arrays.copyOf(byteSequence.getBytes(), byteSequence.getLength());
              text = LoadTextUtil.getTextByBinaryPresentation(bytes, file);
            }
            else {
              text = null;
            }

            FileType detected = null;
            for (FileTypeDetector detector : Extensions.getExtensions(FileTypeDetector.EP_NAME)) {
              try {
                detected = detector.detect(file, byteSequence, text);
              }
              catch (Exception e) {
                LOG.error("Detector " + detector + " (" + detector.getClass() + ") exception occurred:", e);
              }
              if (detected != null) break;
            }

            if (detected == null) {
              detected = isText ? PlainTextFileType.INSTANCE : UnknownFileType.INSTANCE;
            }
            result.set(detected);
            return true;
          }
        });
      }
      finally {
        inputStream.close();
      }
      FileType fileType = result.get();

      if (LOG.isDebugEnabled()) {
        LOG.debug(file + "; type=" + fileType.getDescription() + "; " + DETECTED_COUNT.incrementAndGet());
      }
      return fileType;
    }
    catch (FileNotFoundException e) {
      return UnknownFileType.INSTANCE;
    }
    catch (IOException e) {
      LOG.info(e);
      return UnknownFileType.INSTANCE;
    }
  }

  private static boolean guessIfText(@NotNull VirtualFile file, @NotNull ByteSequence byteSequence) {
    byte[] bytes = byteSequence.getBytes();
    Trinity<Charset, CharsetToolkit.GuessedEncoding, byte[]> guessed = LoadTextUtil.guessFromContent(file, bytes, byteSequence.getLength());
    if (guessed == null) return false;
    file.setBOM(guessed.third);
    if (guessed.first != null) {
      // charset was detected unambiguously
      return true;
    }
    // use wild guess
    CharsetToolkit.GuessedEncoding guess = guessed.second;
    return guess != null && guess != CharsetToolkit.GuessedEncoding.INVALID_UTF8;
  }

  @Override
  public boolean isFileOfType(@NotNull VirtualFile file, @NotNull FileType type) {
    if (type instanceof FileTypeIdentifiableByVirtualFile) {
      return ((FileTypeIdentifiableByVirtualFile)type).isMyFileType(file);
    }

    return getFileTypeByFileName(file.getName()) == type;
  }

  @Override
  @NotNull
  public FileType getFileTypeByExtension(@NotNull String extension) {
    return getFileTypeByFileName("IntelliJ_IDEA_RULES." + extension);
  }

  @Override
  public void registerFileType(FileType fileType) {
    registerFileType(fileType, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  @Override
  public void registerFileType(@NotNull final FileType type, @NotNull final List<FileNameMatcher> defaultAssociations) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        fireBeforeFileTypesChanged();
        registerFileTypeWithoutNotification(type, defaultAssociations);
        fireFileTypesChanged();

      }
    });
  }

  @Override
  public void unregisterFileType(final FileType fileType) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        fireBeforeFileTypesChanged();
        unregisterFileTypeWithoutNotification(fileType);
        fireFileTypesChanged();
      }
    });
  }

  private void unregisterFileTypeWithoutNotification(FileType fileType) {
    removeAllAssociations(fileType);
    mySchemesManager.removeScheme(fileType);
    if (fileType instanceof FileTypeIdentifiableByVirtualFile) {
      final FileTypeIdentifiableByVirtualFile fakeFileType = (FileTypeIdentifiableByVirtualFile)fileType;
      mySpecialFileTypes.remove(fakeFileType);
    }
  }

  @Override
  @NotNull
  public FileType[] getRegisteredFileTypes() {
    Collection<FileType> fileTypes = mySchemesManager.getAllSchemes();
    return fileTypes.toArray(new FileType[fileTypes.size()]);
  }

  @Override
  @NotNull
  public String getExtension(String fileName) {
    int index = fileName.lastIndexOf('.');
    if (index < 0) return "";
    return fileName.substring(index + 1);
  }

  @Override
  @NotNull
  public String getIgnoredFilesList() {
    final Set<String> masks = myIgnoredPatterns.getIgnoreMasks();
    if (masks.isEmpty()) {
      return "";
    }

    return StringUtil.join(masks, ";") + ";";
  }

  @Override
  public void setIgnoredFilesList(@NotNull String list) {
    fireBeforeFileTypesChanged();
    myIgnoredFileCache.clearCache();
    myIgnoredPatterns.setIgnoreMasks(list);
    fireFileTypesChanged();
  }

  @Override
  public boolean isIgnoredFilesListEqualToCurrent(String list) {
    Set<String> tempSet = new THashSet<String>();
    StringTokenizer tokenizer = new StringTokenizer(list, ";");
    while (tokenizer.hasMoreTokens()) {
      tempSet.add(tokenizer.nextToken());
    }
    return tempSet.equals(myIgnoredPatterns.getIgnoreMasks());
  }

  @Override
  public boolean isFileIgnored(@NotNull String name) {
    return myIgnoredPatterns.isIgnored(name);
  }

  @Override
  public boolean isFileIgnored(@NonNls @NotNull VirtualFile file) {
    return myIgnoredFileCache.isFileIgnored(file);
  }

  @Override
  @SuppressWarnings({"deprecation"})
  @NotNull
  public String[] getAssociatedExtensions(@NotNull FileType type) {
    return myPatternsTable.getAssociatedExtensions(type);
  }

  @Override
  @NotNull
  public List<FileNameMatcher> getAssociations(@NotNull FileType type) {
    return myPatternsTable.getAssociations(type);
  }

  @Override
  public void associate(@NotNull FileType type, @NotNull FileNameMatcher matcher) {
    associate(type, matcher, true);
  }

  @Override
  public void removeAssociation(@NotNull FileType type, @NotNull FileNameMatcher matcher) {
    removeAssociation(type, matcher, true);
  }

  private void removeAllAssociations(FileType type) {
    myPatternsTable.removeAllAssociations(type);
  }

  @Override
  public void fireBeforeFileTypesChanged() {
    FileTypeEvent event = new FileTypeEvent(this);
    myMessageBus.syncPublisher(TOPIC).beforeFileTypesChanged(event);
  }

  @Override
  public SchemesManager<FileType, AbstractFileType> getSchemesManager() {
    return mySchemesManager;
  }

  @Override
  public void fireFileTypesChanged() {
    myMessageBus.syncPublisher(TOPIC).fileTypesChanged(new FileTypeEvent(this));
  }

  private final Map<FileTypeListener, MessageBusConnection> myAdapters = new HashMap<FileTypeListener, MessageBusConnection>();

  @Override
  public void addFileTypeListener(@NotNull FileTypeListener listener) {
    final MessageBusConnection connection = myMessageBus.connect();
    connection.subscribe(TOPIC, listener);
    myAdapters.put(listener, connection);
  }

  @Override
  public void removeFileTypeListener(@NotNull FileTypeListener listener) {
    final MessageBusConnection connection = myAdapters.remove(listener);
    if (connection != null) {
      connection.disconnect();
    }
  }


  @SuppressWarnings({"SimplifiableIfStatement"})
  private static boolean isDefaultModified(FileType fileType) {
    if (fileType instanceof ExternalizableFileType) {
      return ((ExternalizableFileType)fileType).isModified();
    }
    return true; //TODO?
  }

  // -------------------------------------------------------------------------
  // Implementation of NamedExternalizable interface
  // -------------------------------------------------------------------------

  @Override
  public String getExternalFileName() {
    return "filetypes";
  }

  @Override
  public void readExternal(Element parentNode) throws InvalidDataException {
    int savedVersion = getVersion(parentNode);
    for (final Object o : parentNode.getChildren()) {
      final Element e = (Element)o;
      if (ELEMENT_FILETYPES.equals(e.getName())) {
        List children = e.getChildren(ELEMENT_FILETYPE);
        for (final Object aChildren : children) {
          Element element = (Element)aChildren;
          loadFileType(element, true, null, false, null);
        }
      }
      else if (ELEMENT_IGNOREFILES.equals(e.getName())) {
        myIgnoredPatterns.setIgnoreMasks(e.getAttributeValue(ATTRIBUTE_LIST));
      }
      else if (AbstractFileType.ELEMENT_EXTENSIONMAP.equals(e.getName())) {
        readGlobalMappings(e);
      }
    }

    if (savedVersion == 0) {
      addIgnore(".svn");
    }
    if (savedVersion < 2) {
      restoreStandardFileExtensions();
    }
    if (savedVersion < 4) {
      addIgnore("*.pyc");
      addIgnore("*.pyo");
      addIgnore(".git");
    }
    if (savedVersion < 5) {
      addIgnore("*.hprof");
    }
    if (savedVersion < 6) {
      addIgnore("_svn");
    }

    if (savedVersion < 7) {
      addIgnore(".hg");
    }

    if (savedVersion < 8) {
      addIgnore("*.lib");
      addIgnore("*~");
    }

    if (savedVersion < 9) {
      addIgnore("__pycache__");
    }

    if (savedVersion < 10) {
      addIgnore(".bundle");
    }

    if (savedVersion < VERSION) {
      addIgnore("*.rbc");
    }
    myIgnoredFileCache.clearCache();
  }

  private void readGlobalMappings(final Element e) {

    List<Pair<FileNameMatcher, String>> associations = AbstractFileType.readAssociations(e);

    for (Pair<FileNameMatcher, String> association : associations) {
      FileType type = getFileTypeByName(association.getSecond());
      if (type != null) {
        associate(type, association.getFirst(), false);
      }
      else {
        myUnresolvedMappings.put(association.getFirst(), association.getSecond());
      }
    }

    List<Trinity<FileNameMatcher, String, Boolean>> removedAssociations = AbstractFileType.readRemovedAssociations(e);

    for (Trinity<FileNameMatcher, String, Boolean> trinity : removedAssociations) {
      FileType type = getFileTypeByName(trinity.getSecond());
      FileNameMatcher matcher = trinity.getFirst();
      if (type != null) {
        removeAssociation(type, matcher, false);
      }
      else {
        myUnresolvedRemovedMappings.put(matcher, Trinity
          .create(trinity.getSecond(), myUnresolvedMappings.get(matcher), trinity.getThird()));
      }
    }
  }

  private void readMappingsForFileType(final Element e, FileType type) {

    List<Pair<FileNameMatcher, String>> associations = AbstractFileType.readAssociations(e);

    for (Pair<FileNameMatcher, String> association : associations) {
      associate(type, association.getFirst(), false);
    }

    List<Trinity<FileNameMatcher, String, Boolean>> removedAssociations = AbstractFileType.readRemovedAssociations(e);

    for (Trinity<FileNameMatcher, String, Boolean> removedAssociation : removedAssociations) {
      removeAssociation(type, removedAssociation.getFirst(), false);
    }

  }

  private void addIgnore(@NonNls final String ignoreMask) {
    myIgnoredPatterns.addIgnoreMask(ignoreMask);
  }

  private void restoreStandardFileExtensions() {
    for (final String name : FILE_TYPES_WITH_PREDEFINED_EXTENSIONS) {
      final StandardFileType stdFileType = myStandardFileTypes.get(name);
      if (stdFileType != null) {
        FileType fileType = stdFileType.fileType;
        for (FileNameMatcher matcher : myPatternsTable.getAssociations(fileType)) {
          FileType defaultFileType = myInitialAssociations.findAssociatedFileType(matcher);
          if (defaultFileType != null && defaultFileType != fileType) {
            removeAssociation(fileType, matcher, false);
            associate(defaultFileType, matcher, false);
          }
        }

        for (FileNameMatcher matcher : myInitialAssociations.getAssociations(fileType)) {
          associate(fileType, matcher, false);
        }
      }
    }
  }

  private static int getVersion(final Element node) {
    final String verString = node.getAttributeValue(ATTRIBUTE_VERSION);
    if (verString == null) return 0;
    try {
      return Integer.parseInt(verString);
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  @Override
  public void writeExternal(Element parentNode) throws WriteExternalException {
    parentNode.setAttribute(ATTRIBUTE_VERSION, String.valueOf(VERSION));

    Element element = new Element(ELEMENT_IGNOREFILES);
    parentNode.addContent(element);
    element.setAttribute(ATTRIBUTE_LIST, getIgnoredFilesList());
    Element map = new Element(AbstractFileType.ELEMENT_EXTENSIONMAP);
    parentNode.addContent(map);

    final List<FileType> fileTypes = Arrays.asList(getRegisteredFileTypes());
    Collections.sort(fileTypes, new Comparator<FileType>() {
      @Override
      public int compare(FileType o1, FileType o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    for (FileType type : fileTypes) {
      writeExtensionsMap(map, type, true);
    }
  }

  private void writeExtensionsMap(final Element map, final FileType type, boolean specifyTypeName) {
    final List<FileNameMatcher> assocs = myPatternsTable.getAssociations(type);
    final Set<FileNameMatcher> defaultAssocs = new HashSet<FileNameMatcher>(myInitialAssociations.getAssociations(type));

    for (FileNameMatcher matcher : assocs) {
      if (defaultAssocs.contains(matcher)) {
        defaultAssocs.remove(matcher);
      }
      else if (shouldSave(type)) {
        if (!(type instanceof ImportedFileType) || !((ImportedFileType)type).getOriginalPatterns().contains(matcher)) {
          Element content = AbstractFileType.writeMapping(type, matcher, specifyTypeName);
          if (content != null) {
            map.addContent(content);
          }
        }
      }
    }

    for (FileNameMatcher matcher : defaultAssocs) {
      Element content = AbstractFileType.writeRemovedMapping(type, matcher, specifyTypeName, isApproved(matcher));
      if (content != null) {
        map.addContent(content);
      }
    }

    if (type instanceof ImportedFileType) {
      List<FileNameMatcher> original = ((ImportedFileType)type).getOriginalPatterns();
      for (FileNameMatcher matcher : original) {
        if (!assocs.contains(matcher)) {
          Element content = AbstractFileType.writeRemovedMapping(type, matcher, specifyTypeName, isApproved(matcher));
          if (content != null) {
            map.addContent(content);
          }
        }
      }
    }
  }

  private boolean isApproved(FileNameMatcher matcher) {
    Pair<FileType, Boolean> pair = myRemovedMappings.get(matcher);
    return pair != null && pair.getSecond();
  }

  // -------------------------------------------------------------------------
  // Helper methods
  // -------------------------------------------------------------------------

  @Nullable
  private FileType getFileTypeByName(String name) {
    return mySchemesManager.findSchemeByName(name);
  }

  private static List<FileNameMatcher> parse(@NonNls String semicolonDelimited) {
    if (semicolonDelimited == null) return Collections.emptyList();
    StringTokenizer tokenizer = new StringTokenizer(semicolonDelimited, FileTypeConsumer.EXTENSION_DELIMITER, false);
    ArrayList<FileNameMatcher> list = new ArrayList<FileNameMatcher>();
    while (tokenizer.hasMoreTokens()) {
      list.add(new ExtensionFileNameMatcher(tokenizer.nextToken().trim()));
    }
    return list;
  }

  /**
   * Registers a standard file type. Doesn't notifyListeners any change events.
   */
  private void registerFileTypeWithoutNotification(FileType fileType, List<FileNameMatcher> matchers) {
    mySchemesManager.addNewScheme(fileType, true);
    for (FileNameMatcher matcher : matchers) {
      myPatternsTable.addAssociation(matcher, fileType);
      myInitialAssociations.addAssociation(matcher, fileType);
    }

    if (fileType instanceof FileTypeIdentifiableByVirtualFile) {
      mySpecialFileTypes.add((FileTypeIdentifiableByVirtualFile)fileType);
    }

  }

  private void bindUnresolvedMappings(FileType fileType) {
    for (FileNameMatcher matcher : new THashSet<FileNameMatcher>(myUnresolvedMappings.keySet())) {
      String name = myUnresolvedMappings.get(matcher);
      if (Comparing.equal(name, fileType.getName())) {
        myPatternsTable.addAssociation(matcher, fileType);
        myUnresolvedMappings.remove(matcher);
      }
    }

    for (FileNameMatcher matcher : new THashSet<FileNameMatcher>(myUnresolvedRemovedMappings.keySet())) {
      Trinity<String, String, Boolean> trinity = myUnresolvedRemovedMappings.get(matcher);
      if (Comparing.equal(trinity.getFirst(), fileType.getName())) {
        removeAssociation(fileType, matcher, false);
        myUnresolvedRemovedMappings.remove(matcher);
      }
    }
  }

  // returns true if at least one standard file type has been read
  @SuppressWarnings({"EmptyCatchBlock"})
  private boolean loadAllFileTypes() {
    Collection<AbstractFileType> collection = mySchemesManager.loadSchemes();

    boolean res = false;
    for (AbstractFileType fileType : collection) {
      ReadFileType readFileType = (ReadFileType)fileType;
      FileType loadedFileType = loadFileType(readFileType);
      res |= myInitialAssociations.hasAssociationsFor(loadedFileType);
    }

    return res;

  }

  private FileType loadFileType(final ReadFileType readFileType) {
    return loadFileType(readFileType.getElement(), false, mySchemesManager.isShared(readFileType) ? readFileType.getExternalInfo() : null,
                        true, readFileType.getExternalInfo().getCurrentFileName());
  }


  private FileType loadFileType(Element typeElement, boolean isDefaults, final ExternalInfo info, boolean ignoreExisting, String fileName) {
    String fileTypeName = typeElement.getAttributeValue(ATTRIBUTE_NAME);
    String fileTypeDescr = typeElement.getAttributeValue(ATTRIBUTE_DESCRIPTION);
    String iconPath = typeElement.getAttributeValue(ATTRIBUTE_ICON);
    String extensionsStr = typeElement.getAttributeValue(ATTRIBUTE_EXTENSIONS); // TODO: support wildcards

    FileType type = getFileTypeByName(fileTypeName);

    if (isDefaults && !ignoreExisting) {
      extensionsStr = filterAlreadyRegisteredExtensions(extensionsStr);
    }

    List<FileNameMatcher> exts = parse(extensionsStr);
    if (type != null && !ignoreExisting) {
      if (isDefaults) return type;
      if (extensionsStr != null) {
        removeAllAssociations(type);
        for (FileNameMatcher ext : exts) {
          associate(type, ext, false);
        }
      }

      if (type instanceof JDOMExternalizable) {
        try {
          ((JDOMExternalizable)type).readExternal(typeElement);
        }
        catch (InvalidDataException e) {
          throw new RuntimeException(e);
        }
      }
    }
    else {
      type = loadCustomFile(typeElement, info, fileName);
      if (type instanceof UserFileType) {
        setFileTypeAttributes(fileTypeName, fileTypeDescr, iconPath, (UserFileType)type);
      }
      registerFileTypeWithoutNotification(type, exts);
    }

    if (type instanceof UserFileType) {
      UserFileType ft = (UserFileType)type;
      setFileTypeAttributes(fileTypeName, fileTypeDescr, iconPath, ft);
    }

    if (isDefaults) {
      myDefaultTypes.add(type);
      if (type instanceof ExternalizableFileType) {
        ((ExternalizableFileType)type).markDefaultSettings();
      }
    }
    else {
      Element extensions = typeElement.getChild(AbstractFileType.ELEMENT_EXTENSIONMAP);
      if (extensions != null) {
        readMappingsForFileType(extensions, type);
      }
    }

    return type;
  }

  private String filterAlreadyRegisteredExtensions(String semicolonDelimited) {
    StringTokenizer tokenizer = new StringTokenizer(semicolonDelimited, FileTypeConsumer.EXTENSION_DELIMITER, false);
    ArrayList<String> list = new ArrayList<String>();
    while (tokenizer.hasMoreTokens()) {
      final String extension = tokenizer.nextToken().trim();
      if (getFileTypeByExtension(extension) == UnknownFileType.INSTANCE) {
        list.add(extension);
      }
    }
    return StringUtil.join(list, FileTypeConsumer.EXTENSION_DELIMITER);
  }

  private static FileType loadCustomFile(final Element typeElement, ExternalInfo info, String fileName) {
    FileType type = null;

    Element element = typeElement.getChild(AbstractFileType.ELEMENT_HIGHLIGHTING);
    if (element != null) {
      final SyntaxTable table = AbstractFileType.readSyntaxTable(element);
      if (table != null) {
        if (info == null) {
          type = new AbstractFileType(table);
          ((AbstractFileType)type).getExternalInfo().setCurrentFileName(fileName);
        }
        else {
          type = new ImportedFileType(table, info);
          ((ImportedFileType)type).readOriginalMatchers(typeElement);
        }
        ((AbstractFileType)type).initSupport();
        return type;
      }
    }
    for (CustomFileTypeFactory factory : Extensions.getExtensions(CustomFileTypeFactory.EP_NAME)) {
      type = factory.createFileType(typeElement);
      if (type != null) {
        break;
      }
    }
    if (type == null) {
      type = new UserBinaryFileType();
    }
    return type;
  }

  private static void setFileTypeAttributes(final String fileTypeName,
                                            final String fileTypeDescr,
                                            final String iconPath,
                                            final UserFileType ft) {
    if (iconPath != null && !StringUtil.isEmptyOrSpaces(iconPath)) {
      Icon icon = IconLoader.getIcon(iconPath);
      ft.setIcon(icon);
    }

    if (fileTypeDescr != null) ft.setDescription(fileTypeDescr);
    if (fileTypeName != null) ft.setName(fileTypeName);
  }

  private static File getOrCreateFileTypesDir() {
    String directoryPath = PathManager.getConfigPath() + File.separator + ELEMENT_FILETYPES;
    File directory = new File(directoryPath);
    if (!directory.exists()) {
      if (!directory.mkdir()) {
        LOG.error("Could not create directory: " + directory.getAbsolutePath());
        return null;
      }
    }
    return directory;
  }

  private static boolean shouldSave(FileType fileType) {
    return fileType != FileTypes.UNKNOWN && !fileType.isReadOnly();
  }

  private static void writeHeader(Element root, FileType fileType) {
    root.setAttribute(ATTRIBUTE_BINARY, String.valueOf(fileType.isBinary()));
    root.setAttribute(ATTRIBUTE_DEFAULT_EXTENSION, fileType.getDefaultExtension());
    root.setAttribute(ATTRIBUTE_DESCRIPTION, fileType.getDescription());
    root.setAttribute(ATTRIBUTE_NAME, fileType.getName());
  }

  // -------------------------------------------------------------------------
  // Setup
  // -------------------------------------------------------------------------

  @Override
  @NotNull
  public String getComponentName() {
    return getFileTypeComponentName();
  }

  public static String getFileTypeComponentName() {
    return PlatformUtils.isCommunity() ? "CommunityFileTypes" : "FileTypeManager";
  }

  public FileTypeAssocTable getExtensionMap() {
    return myPatternsTable;
  }

  public void setPatternsTable(@NotNull Set<FileType> fileTypes, @NotNull FileTypeAssocTable<FileType> assocTable) {
    fireBeforeFileTypesChanged();
    for (FileType existing : getRegisteredFileTypes()) {
      if (!fileTypes.contains(existing)) {
        mySchemesManager.removeScheme(existing);
      }
    }
    for (FileType fileType : fileTypes) {
      mySchemesManager.addNewScheme(fileType, true);
      if (fileType instanceof AbstractFileType) {
        ((AbstractFileType)fileType).initSupport();
      }
    }
    myPatternsTable = assocTable.copy();
    fireFileTypesChanged();
  }

  public void associate(FileType fileType, FileNameMatcher matcher, boolean fireChange) {
    if (!myPatternsTable.isAssociatedWith(fileType, matcher)) {
      if (fireChange) {
        fireBeforeFileTypesChanged();
      }
      myPatternsTable.addAssociation(matcher, fileType);
      if (fireChange) {
        fireFileTypesChanged();
      }
    }
  }

  public void removeAssociation(FileType fileType, FileNameMatcher matcher, boolean fireChange) {
    if (myPatternsTable.isAssociatedWith(fileType, matcher)) {
      if (fireChange) {
        fireBeforeFileTypesChanged();
      }
      myPatternsTable.removeAssociation(matcher, fileType);
      if (fireChange) {
        fireFileTypesChanged();
      }
    }
  }

  @Override
  @Nullable
  public FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile file) {
    FileType type = file.getFileType();
    if (type != UnknownFileType.INSTANCE) return type;
    return FileTypeChooser.getKnownFileTypeOrAssociate(file.getName());
  }

  @Override
  public FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile file, @NotNull Project project) {
    return FileTypeChooser.getKnownFileTypeOrAssociate(file, project);
  }

  private void registerReDetectedMappings(StandardFileType pair) {
    FileType fileType = pair.fileType;
    if (fileType == PlainTextFileType.INSTANCE) return;
    for (FileNameMatcher matcher : pair.matchers) {
      String typeName = myUnresolvedMappings.get(matcher);
      if (typeName != null && !typeName.equals(fileType.getName())) {
        Trinity<String, String, Boolean> trinity = myUnresolvedRemovedMappings.get(matcher);
        myRemovedMappings.put(matcher, Pair.create(fileType, trinity != null && trinity.third));
      }
    }
  }

  Map<FileNameMatcher, Pair<FileType, Boolean>> getRemovedMappings() {
    return myRemovedMappings;
  }
}
