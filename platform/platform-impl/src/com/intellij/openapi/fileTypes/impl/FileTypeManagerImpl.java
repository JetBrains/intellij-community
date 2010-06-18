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
package com.intellij.openapi.fileTypes.impl;

import com.intellij.AppTopics;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.ide.highlighter.custom.impl.ReadFileType;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.*;
import com.intellij.openapi.options.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PatternUtil;
import com.intellij.util.containers.ConcurrentHashSet;
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
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author Yura Cangea
 */
public class FileTypeManagerImpl extends FileTypeManagerEx implements NamedJDOMExternalizable, ExportableApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl");
  private static final int VERSION = 7;

  private final Set<FileType> myDefaultTypes = new THashSet<FileType>();
  private final ArrayList<FileTypeIdentifiableByVirtualFile> mySpecialFileTypes = new ArrayList<FileTypeIdentifiableByVirtualFile>();
  private final ArrayList<Pattern> myIgnorePatterns = new ArrayList<Pattern>();

  private FileTypeAssocTable<FileType> myPatternsTable = new FileTypeAssocTable<FileType>();
  private final Set<String> myIgnoredFileMasksSet = new LinkedHashSet<String>();
  private final Set<String> myNotIgnoredFiles = new ConcurrentHashSet<String>();
  private final Set<String> myIgnoredFiles = new ConcurrentHashSet<String>();
  private final FileTypeAssocTable<FileType> myInitialAssociations = new FileTypeAssocTable<FileType>();
  private final Map<FileNameMatcher, String> myUnresolvedMappings = new THashMap<FileNameMatcher, String>();
  private final Map<FileNameMatcher, String> myUnresolvedRemovedMappings = new THashMap<FileNameMatcher, String>();

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
  private static final Map<String, StandardFileType> ourStandardFileTypes = new LinkedHashMap<String, StandardFileType>();
  @NonNls private static final String[] FILE_TYPES_WITH_PREDEFINED_EXTENSIONS = {"JSP", "JSPX", "DTD", "HTML", "Properties", "XHTML"};
  private final SchemesManager<FileType, AbstractFileType> mySchemesManager;
  @NonNls private static final String FILE_SPEC = "$ROOT_CONFIG$/filetypes";

  static {
    final FileTypeConsumer consumer = new FileTypeConsumer() {
      public void consume(@NotNull final FileType fileType, final String extensions) {
        register(fileType, parse(extensions));
      }

      public void consume(@NotNull final FileType fileType, final FileNameMatcher... matchers) {
        register(fileType, new ArrayList<FileNameMatcher>(Arrays.asList(matchers)));
      }

      public FileType getStandardFileTypeByName(@NotNull final String name) {
        final StandardFileType type = ourStandardFileTypes.get(name);
        return type != null ? type.fileType : null;
      }

      private void register(final FileType fileType, final List<FileNameMatcher> fileNameMatchers) {
        final StandardFileType type = ourStandardFileTypes.get(fileType.getName());

        if (type != null) {
          for (FileNameMatcher matcher : fileNameMatchers) type.matchers.add(matcher);
        }
        else {
          ourStandardFileTypes.put(fileType.getName(), new StandardFileType(fileType, fileNameMatchers));
        }
      }
    };
    final FileTypeFactory[] fileTypeFactories = Extensions.getExtensions(FileTypeFactory.FILE_TYPE_FACTORY_EP);
    for (final FileTypeFactory factory : fileTypeFactories) {
      try {
        initFactory(consumer, factory);
      }
      catch (final Error ex) {
        PluginManager.disableIncompatiblePlugin(factory, ex);
      }
    }
  }

  private static void initFactory(final FileTypeConsumer consumer, final FileTypeFactory factory) {
    factory.createFileTypes(consumer);
  }

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  public FileTypeManagerImpl(MessageBus bus, SchemesManagerFactory schemesManagerFactory) {
    myMessageBus = bus;
    mySchemesManager = schemesManagerFactory.createSchemesManager(FILE_SPEC, new BaseSchemeProcessor<AbstractFileType>() {
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

      public boolean shouldBeSaved(final AbstractFileType fileType) {
        return shouldBeSavedToFile(fileType);

      }

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

      public void onSchemeAdded(final AbstractFileType scheme) {
        fireBeforeFileTypesChanged();
        if (scheme instanceof ReadFileType) {
          loadFileType((ReadFileType)scheme);
        }
        fireFileTypesChanged();
      }

      public void onSchemeDeleted(final AbstractFileType scheme) {
        fireBeforeFileTypesChanged();
        myPatternsTable.removeAllAssociations(scheme);
        fireFileTypesChanged();
      }
    }, RoamingType.PER_USER);
    for (final StandardFileType pair : ourStandardFileTypes.values()) {
      registerFileTypeWithoutNotification(pair.fileType, pair.matchers);
    }
    if (loadAllFileTypes()) {
      restoreStandardFileExtensions();
    }
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

  @NotNull
  public FileType getStdFileType(@NotNull @NonNls String name) {
    StandardFileType stdFileType = ourStandardFileTypes.get(name);
    return stdFileType != null ? stdFileType.fileType : new PlainTextFileType();
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{getOrCreateFileTypesDir(), PathManager.getOptionsFile(this)};
  }

  @NotNull
  public String getPresentableName() {
    return FileTypesBundle.message("filetype.settings.component");
  }
  // -------------------------------------------------------------------------
  // ApplicationComponent interface implementation
  // -------------------------------------------------------------------------

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  // -------------------------------------------------------------------------
  // Implementation of abstract methods
  // -------------------------------------------------------------------------

  @NotNull
  public FileType getFileTypeByFileName(@NotNull String fileName) {
    FileType type = myPatternsTable.findAssociatedFileType(fileName);
    return type == null ? UnknownFileType.INSTANCE : type;
  }

  @NotNull
  public FileType getFileTypeByFile(@NotNull VirtualFile file) {
    // first let file recognize its type
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < mySpecialFileTypes.size(); i++) {
      final FileTypeIdentifiableByVirtualFile fileType = mySpecialFileTypes.get(i);
      if (fileType.isMyFileType(file)) return fileType;
    }

    return getFileTypeByFileName(file.getName());
  }

  public boolean isFileOfType(VirtualFile file, FileType type) {
    if (type instanceof FileTypeIdentifiableByVirtualFile) {
      return ((FileTypeIdentifiableByVirtualFile) type).isMyFileType(file);
    }
    final List<FileNameMatcher> matchers = getAssociations(type);
    for (FileNameMatcher matcher : matchers) {
      if (matcher.accept(file.getName())) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public FileType getFileTypeByExtension(@NotNull String extension) {
    return getFileTypeByFileName("IntelliJ_IDEA_RULES." + extension);
  }

  public void registerFileType(FileType fileType) {
    registerFileType(fileType, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public void registerFileType(@NotNull final FileType type, @NotNull final List<FileNameMatcher> defaultAssociations) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        fireBeforeFileTypesChanged();
        registerFileTypeWithoutNotification(type, defaultAssociations);
        fireFileTypesChanged();

      }
    });
  }

  public void unregisterFileType(FileType fileType) {
    fireBeforeFileTypesChanged();
    unregisterFileTypeWithoutNotification(fileType);
    fireFileTypesChanged();
  }

  private void unregisterFileTypeWithoutNotification(FileType fileType) {
    removeAllAssociations(fileType);
    mySchemesManager.removeScheme(fileType);
    if (fileType instanceof FileTypeIdentifiableByVirtualFile) {
      final FileTypeIdentifiableByVirtualFile fakeFileType = (FileTypeIdentifiableByVirtualFile)fileType;
      mySpecialFileTypes.remove(fakeFileType);
    }
  }

  @NotNull
  public FileType[] getRegisteredFileTypes() {
    Collection<FileType> fileTypes = mySchemesManager.getAllSchemes();
    return fileTypes.toArray(new FileType[fileTypes.size()]);
  }

  @NotNull
  public String getExtension(String fileName) {
    int index = fileName.lastIndexOf('.');
    if (index < 0) return "";
    return fileName.substring(index + 1);
  }

  @NotNull
  public String getIgnoredFilesList() {
    StringBuilder sb = new StringBuilder();
    for (String ignoreMask : myIgnoredFileMasksSet) {
      sb.append(ignoreMask);
      sb.append(';');
    }
    return sb.toString();
  }

  public void setIgnoredFilesList(@NotNull String list) {
    fireBeforeFileTypesChanged();
    setIgnoredFilesListWithoutNotification(list);

    fireFileTypesChanged();
  }

  private void setIgnoredFilesListWithoutNotification(String list) {
    myIgnoredFileMasksSet.clear();
    myIgnorePatterns.clear();

    StringTokenizer tokenizer = new StringTokenizer(list, ";");
    while (tokenizer.hasMoreTokens()) {
      String ignoredFile = tokenizer.nextToken();
      if (ignoredFile != null && !myIgnoredFileMasksSet.contains(ignoredFile)) {
        if (!myIgnoredFileMasksSet.contains(ignoredFile)) {
          myIgnorePatterns.add(PatternUtil.fromMask(ignoredFile));
        }
        myIgnoredFileMasksSet.add(ignoredFile);
      }
    }

    //[mike]
    //To make async delete work. See FileUtil.asyncDelete.
    //Quite a hack, but still we need to have some name, which
    //won't be catched by VF for sure.
    //noinspection HardCodedStringLiteral
    Pattern p = Pattern.compile(".*\\.__del__");
    myIgnorePatterns.add(p);
  }

  public boolean isIgnoredFilesListEqualToCurrent(String list) {
    Set<String> tempSet = new THashSet<String>();
    StringTokenizer tokenizer = new StringTokenizer(list, ";");
    while (tokenizer.hasMoreTokens()) {
      tempSet.add(tokenizer.nextToken());
    }
    return tempSet.equals(myIgnoredFileMasksSet);
  }

  public boolean isFileIgnored(@NotNull String name) {
    if (myNotIgnoredFiles.contains(name)) return false;
    if (myIgnoredFiles.contains(name)) return true;

    for (Pattern pattern : myIgnorePatterns) {
      if (pattern.matcher(name).matches()) {
        myIgnoredFiles.add(name);
        return true;
      }
    }

    myNotIgnoredFiles.add(name);
    return false;
  }

  @SuppressWarnings({"deprecation"})
  @NotNull
  public String[] getAssociatedExtensions(@NotNull FileType type) {
    return myPatternsTable.getAssociatedExtensions(type);
  }

  @NotNull
  public List<FileNameMatcher> getAssociations(@NotNull FileType type) {
    return myPatternsTable.getAssociations(type);
  }

  public void associate(@NotNull FileType type, @NotNull FileNameMatcher matcher) {
    associate(type, matcher, true);
  }

  public void removeAssociation(@NotNull FileType type, @NotNull FileNameMatcher matcher) {
    removeAssociation(type, matcher, true);
  }

  private void removeAllAssociations(FileType type) {
    myPatternsTable.removeAllAssociations(type);
  }

  public void fireBeforeFileTypesChanged() {
    FileTypeEvent event = new FileTypeEvent(this);
    myMessageBus.syncPublisher(AppTopics.FILE_TYPES).beforeFileTypesChanged(event);
  }

  public SchemesManager<FileType, AbstractFileType> getSchemesManager() {
    return mySchemesManager;
  }

  public void fireFileTypesChanged() {
    myNotIgnoredFiles.clear();
    myIgnoredFiles.clear();

    final FileTypeEvent event = new FileTypeEvent(this);
    myMessageBus.syncPublisher(AppTopics.FILE_TYPES).fileTypesChanged(event);
  }

  private final Map<FileTypeListener, MessageBusConnection> myAdapters = new HashMap<FileTypeListener, MessageBusConnection>();

  public void addFileTypeListener(@NotNull FileTypeListener listener) {
    final MessageBusConnection connection = myMessageBus.connect();
    connection.subscribe(AppTopics.FILE_TYPES, listener);
    myAdapters.put(listener, connection);
  }

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

  public String getExternalFileName() {
    return "filetypes";
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    int savedVersion = getVersion(parentNode);
    for (final Object o : parentNode.getChildren()) {
      final Element e = (Element)o;
      if (ELEMENT_FILETYPES.equals(e.getName())) {
        List children = e.getChildren(ELEMENT_FILETYPE);
        for (final Object aChildren : children) {
          Element element = (Element)aChildren;
          loadFileType(element, true, null, false);
        }
      }
      else if (ELEMENT_IGNOREFILES.equals(e.getName())) {
        setIgnoredFilesListWithoutNotification(e.getAttributeValue(ATTRIBUTE_LIST));
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

    if (savedVersion < VERSION) {
      addIgnore(".hg");
    }
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

    List<Pair<FileNameMatcher, String>> removedAssociations = AbstractFileType.readRemovedAssociations(e);

    for (Pair<FileNameMatcher, String> removedAssociation : removedAssociations) {
      FileType type = getFileTypeByName(removedAssociation.getSecond());
      if (type != null) {
        removeAssociation(type, removedAssociation.getFirst(), false);
      }
      else {
        myUnresolvedRemovedMappings.put(removedAssociation.getFirst(), removedAssociation.getSecond());
      }
    }
  }

  private void readMappingsForFileType(final Element e, FileType type) {

    List<Pair<FileNameMatcher, String>> associations = AbstractFileType.readAssociations(e);

    for (Pair<FileNameMatcher, String> association : associations) {
      associate(type, association.getFirst(), false);
    }

    List<Pair<FileNameMatcher, String>> removedAssociations = AbstractFileType.readRemovedAssociations(e);

    for (Pair<FileNameMatcher, String> removedAssociation : removedAssociations) {
      removeAssociation(type, removedAssociation.getFirst(), false);
    }

  }

  private void addIgnore(@NonNls final String ignoreMask) {
    if (!myIgnoredFileMasksSet.contains(ignoreMask)) {
      myIgnorePatterns.add(PatternUtil.fromMask(ignoreMask));
      myIgnoredFileMasksSet.add(ignoreMask);
    }
  }

  private void restoreStandardFileExtensions() {
    for (final String name : FILE_TYPES_WITH_PREDEFINED_EXTENSIONS) {
      final StandardFileType stdFileType = ourStandardFileTypes.get(name);
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

  public void writeExternal(Element parentNode) throws WriteExternalException {
    parentNode.setAttribute(ATTRIBUTE_VERSION, String.valueOf(VERSION));

    Element element = new Element(ELEMENT_IGNOREFILES);
    parentNode.addContent(element);
    element.setAttribute(ATTRIBUTE_LIST, getIgnoredFilesList());
    Element map = new Element(AbstractFileType.ELEMENT_EXTENSIONMAP);
    parentNode.addContent(map);

    final List<FileType> fileTypes = Arrays.asList(getRegisteredFileTypes());
    Collections.sort(fileTypes, new Comparator<FileType>() {
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
      Element content = AbstractFileType.writeRemovedMapping(type, matcher, specifyTypeName);
      if (content != null) {
        map.addContent(content);
      }
    }

    if (type instanceof ImportedFileType) {
      List<FileNameMatcher> original = ((ImportedFileType)type).getOriginalPatterns();
      for (FileNameMatcher matcher : original) {
        if (!assocs.contains(matcher)) {
          Element content = AbstractFileType.writeRemovedMapping(type, matcher, specifyTypeName);
          if (content != null) {
            map.addContent(content);
          }
        }
      }
    }
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

    // Resolve unresolved mappings initialized before certain plugin initialized.
    for (FileNameMatcher matcher : new THashSet<FileNameMatcher>(myUnresolvedMappings.keySet())) {
      String name = myUnresolvedMappings.get(matcher);
      if (Comparing.equal(name, fileType.getName())) {
        myPatternsTable.addAssociation(matcher, fileType);
        myUnresolvedMappings.remove(matcher);
      }
    }

    for (FileNameMatcher matcher : new THashSet<FileNameMatcher>(myUnresolvedRemovedMappings.keySet())) {
      String name = myUnresolvedRemovedMappings.get(matcher);
      if (Comparing.equal(name, fileType.getName())) {
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
                        true);
  }


  private FileType loadFileType(Element typeElement, boolean isDefaults, final ExternalInfo info, boolean ignoreExisting) {
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
      type = loadCustomFile(typeElement, info);
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

  private static FileType loadCustomFile(final Element typeElement, ExternalInfo info) {
    FileType type = null;

    Element element = typeElement.getChild(AbstractFileType.ELEMENT_HIGHLIGHTING);
    if (element != null) {
      final SyntaxTable table = AbstractFileType.readSyntaxTable(element);
      if (table != null) {
        if (info == null) {
          type = new AbstractFileType(table);
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
    if (iconPath != null && !"".equals(iconPath.trim())) {
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

  @NotNull
  public String getComponentName() {
    if ("Idea".equals(System.getProperty("idea.platform.prefix"))) {
      return "CommunityFileTypes";
    }
    return "FileTypeManager";
  }

  public FileTypeAssocTable getExtensionMap() {
    return myPatternsTable;
  }

  public void setPatternsTable(Set<FileType> fileTypes, FileTypeAssocTable assocTable) {
    fireBeforeFileTypesChanged();
    mySchemesManager.clearAllSchemes();
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

  @Nullable
  public FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile file) {
    return FileTypeChooser.getKnownFileTypeOrAssociate(file);
  }
}
