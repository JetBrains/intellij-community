// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.highlighter.WorkspaceFileType;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.FrequentEventDetector;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.PersistentFSConstants;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.PatternUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@SuppressWarnings("ConstantConditions")
public class FileTypesTest extends PlatformTestCase {
  private FileTypeManagerImpl myFileTypeManager;
  private String myOldIgnoredFilesList;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFileTypeManager = (FileTypeManagerImpl)FileTypeManagerEx.getInstanceEx();
    myOldIgnoredFilesList = myFileTypeManager.getIgnoredFilesList();
    FileTypeManagerImpl.reDetectAsync(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      FileTypeManagerImpl.reDetectAsync(false);
      ApplicationManager.getApplication().runWriteAction(() -> myFileTypeManager.setIgnoredFilesList(myOldIgnoredFilesList));
      myFileTypeManager.clearForTests();
      myFileTypeManager.initStandardFileTypes();
      myFileTypeManager.initializeComponent();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myFileTypeManager = null;
      super.tearDown();
    }
  }

  public void testMaskExclude() {
    final String pattern1 = "a*b.c?d";
    final String pattern2 = "xxx";
    ApplicationManager.getApplication().runWriteAction(() -> myFileTypeManager.setIgnoredFilesList(pattern1 + ";" + pattern2));
    checkIgnored("ab.cxd");
    checkIgnored("axb.cxd");
    checkIgnored("xxx");
    checkNotIgnored("ax.cxx");
    checkNotIgnored("ab.cd");
    checkNotIgnored("ab.c__d");
    checkNotIgnored("xxxx");
    checkNotIgnored("xx");
    assertTrue(myFileTypeManager.isIgnoredFilesListEqualToCurrent(pattern2 + ";" + pattern1));
    assertFalse(myFileTypeManager.isIgnoredFilesListEqualToCurrent(pattern2 + ";" + "ab.c*d"));
  }

  public void testExcludePerformance() {
    ApplicationManager.getApplication().runWriteAction(() -> myFileTypeManager.setIgnoredFilesList("1*2;3*4;5*6;7*8;9*0;*1;*3;*5;*6;7*;*8*"));
    final String[] names = new String[100];
    for (int i = 0; i < names.length; i++) {
      String name = String.valueOf(i % 10 * 10 + i * 100 + i + 1);
      names[i] = name + name + name + name;
    }
    PlatformTestUtil.startPerformanceTest("isFileIgnored", 19_000, () -> {
      for (int i = 0; i < 100_000; i++) {
        for (String name : names) {
          myFileTypeManager.isFileIgnored(name);
        }
      }
    }).assertTiming();
  }

  public void testMaskToPattern() {
    for (char i = 0; i < 256; i++) {
      if (i == '?' || i == '*') continue;
      String str = "x" + i + "y";
      assertTrue("char: " + i + "(" + (int)i + ")", PatternUtil.fromMask(str).matcher(str).matches());
    }
    String allSymbols = "+.\\*/^?$[]()";
    assertTrue(PatternUtil.fromMask(allSymbols).matcher(allSymbols).matches());
    Pattern pattern = PatternUtil.fromMask("?\\?/*");
    assertTrue(pattern.matcher("a\\b/xyz").matches());
    assertFalse(pattern.matcher("x/a\\b").matches());
  }

  public void testAddNewExtension() {
    FileTypeAssocTable<FileType> associations = new FileTypeAssocTable<>();
    associations.addAssociation(FileTypeManager.parseFromString("*.java"), ArchiveFileType.INSTANCE);
    associations.addAssociation(FileTypeManager.parseFromString("*.xyz"), StdFileTypes.XML);
    associations.addAssociation(FileTypeManager.parseFromString("SomeSpecial*.java"), StdFileTypes.XML); // patterns should have precedence over extensions
    assertEquals(StdFileTypes.XML, associations.findAssociatedFileType("sample.xyz"));
    assertEquals(StdFileTypes.XML, associations.findAssociatedFileType("SomeSpecialFile.java"));
    checkNotAssociated(StdFileTypes.XML, "java", associations);
    checkNotAssociated(StdFileTypes.XML, "iws", associations);
  }

  public void testIgnoreOrder() {
    final FileTypeManagerEx manager = FileTypeManagerEx.getInstanceEx();
    ApplicationManager.getApplication().runWriteAction(() -> manager.setIgnoredFilesList("a;b;"));
    assertEquals("a;b;", manager.getIgnoredFilesList());
    ApplicationManager.getApplication().runWriteAction(() -> manager.setIgnoredFilesList("b;a;"));
    assertEquals("b;a;", manager.getIgnoredFilesList());
  }

  public void testIgnoredFiles() throws IOException {
    File file = createTempFile(".svn", "");
    VirtualFile vFile = getVirtualFile(file);
    assertTrue(FileTypeManager.getInstance().isFileIgnored(vFile));
    VfsTestUtil.deleteFile(vFile);

    file = createTempFile("a.txt", "");
    vFile = getVirtualFile(file);
    assertFalse(FileTypeManager.getInstance().isFileIgnored(vFile));
  }


  @SuppressWarnings("deprecation")
  private static void checkNotAssociated(FileType fileType, String extension, FileTypeAssocTable<FileType> associations) {
    assertFalse(Arrays.asList(associations.getAssociatedExtensions(fileType)).contains(extension));
  }

  private void checkNotIgnored(String fileName) {
    assertFalse(myFileTypeManager.isFileIgnored(fileName));
  }

  private void checkIgnored(String fileName) {
    assertTrue(myFileTypeManager.isFileIgnored(fileName));
  }

  public void testAutoDetected() throws IOException {
    File dir = createTempDirectory();
    File file = FileUtil.createTempFile(dir, "x", "xxx_xx_xx", true);
    FileUtil.writeToFile(file, "xxx xxx xxx xxx");
    VirtualFile virtualFile = getVirtualFile(file);
    assertNotNull(virtualFile);
    PsiFile psi = getPsiManager().findFile(virtualFile);
    assertTrue(psi instanceof PsiPlainTextFile);
    assertEquals(FileTypes.PLAIN_TEXT, virtualFile.getFileType());
  }

  public void testAutoDetectedWhenDocumentWasCreated() throws IOException {
    File dir = createTempDirectory();
    File file = FileUtil.createTempFile(dir, "x", "xxx_xx_xx", true);
    FileUtil.writeToFile(file, "xxx xxx xxx xxx");
    VirtualFile virtualFile = getVirtualFile(file);
    assertNotNull(virtualFile);
    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    assertNotNull(document);
    assertEquals(FileTypes.PLAIN_TEXT, virtualFile.getFileType());
  }

  public void testAutoDetectionShouldNotBeOverEager() throws IOException {
    File dir = createTempDirectory();
    File file = FileUtil.createTempFile(dir, "x", "xxx_xx_xx", true);
    FileUtil.writeToFile(file, "xxx xxx xxx xxx");
    VirtualFile virtualFile = getVirtualFile(file);
    assertNotNull(virtualFile);
    TestCase.assertEquals(PlainTextFileType.INSTANCE, virtualFile.getFileType());
  }

  public void testAutoDetectEmptyFile() throws IOException {
    File dir = createTempDirectory();
    File file = FileUtil.createTempFile(dir, "x", "xxx_xx_xx", true);
    VirtualFile virtualFile = getVirtualFile(file);
    assertNotNull(virtualFile);
    assertEquals(FileTypes.UNKNOWN, virtualFile.getFileType());
    PsiFile psi = getPsiManager().findFile(virtualFile);
    assertTrue(psi instanceof PsiBinaryFile);
    assertEquals(FileTypes.UNKNOWN, virtualFile.getFileType());

    setBinaryContent(virtualFile, "xxxxxxx".getBytes(StandardCharsets.UTF_8));
    assertEquals(FileTypes.PLAIN_TEXT, virtualFile.getFileType());
    PsiFile after = getPsiManager().findFile(virtualFile);
    assertNotSame(psi, after);
    assertFalse(psi.isValid());
    assertTrue(after.isValid());
    assertTrue(after instanceof PsiPlainTextFile);
  }

  public void testAutoDetectTextFileFromContents() throws IOException {
    File dir = createTempDirectory();
    VirtualFile vDir = getVirtualFile(dir);
    VirtualFile vFile = createChildData(vDir, "test.xxxxxxxx");
    setFileText(vFile, "text");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    assertEquals(PlainTextFileType.INSTANCE, vFile.getFileType()); // type autodetected during indexing

    PsiFile psiFile = PsiManagerEx.getInstanceEx(getProject()).getFileManager().findFile(vFile); // autodetect text file if needed
    assertNotNull(psiFile);
    assertEquals(PlainTextFileType.INSTANCE, psiFile.getFileType());
  }

  public void testAutoDetectTextFileEvenOutsideTheProject() throws IOException {
    File d = createTempDirectory();
    File f = new File(d, "xx.asfdasdfas");
    FileUtil.writeToFile(f, "asdasdasdfafds");
    VirtualFile vFile = getVirtualFile(f);

    assertEquals(PlainTextFileType.INSTANCE, vFile.getFileType());
  }

  public void test7BitBinaryIsNotText() throws IOException {
    File d = createTempDirectory();
    byte[] bytes = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 'x', 'a', 'b'};
    assertEquals(CharsetToolkit.GuessedEncoding.BINARY, new CharsetToolkit(bytes).guessFromContent(bytes.length));
    File f = new File(d, "xx.asfdasdfas");
    FileUtil.writeToFile(f, bytes);

    VirtualFile vFile = getVirtualFile(f);

    assertEquals(UnknownFileType.INSTANCE, vFile.getFileType());
  }

  public void test7BitIsText() throws IOException {
    File d = createTempDirectory();
    byte[] bytes = {9, 10, 13, 'x', 'a', 'b'};
    assertEquals(CharsetToolkit.GuessedEncoding.SEVEN_BIT, new CharsetToolkit(bytes).guessFromContent(bytes.length));
    File f = new File(d, "xx.asfdasdfas");
    FileUtil.writeToFile(f, bytes);
    VirtualFile vFile = getVirtualFile(f);

    assertEquals(PlainTextFileType.INSTANCE, vFile.getFileType());
  }

  public void testReDetectOnContentsChange() throws IOException {
    final FileTypeRegistry fileTypeManager = FileTypeRegistry.getInstance();
    assertTrue(fileTypeManager.getClass().getName(), fileTypeManager instanceof FileTypeManagerImpl);
    FileType fileType = fileTypeManager.getFileTypeByFileName("x" + ModuleFileType.DOT_DEFAULT_EXTENSION);
    assertTrue(fileType.toString(), fileType instanceof ModuleFileType);
    fileType = fileTypeManager.getFileTypeByFileName("x" + ProjectFileType.DOT_DEFAULT_EXTENSION);
    assertTrue(fileType.toString(), fileType instanceof ProjectFileType);
    FileType module = fileTypeManager.findFileTypeByName("IDEA_MODULE");
    assertNotNull(module);
    assertFalse(module.equals(PlainTextFileType.INSTANCE));
    FileType project = fileTypeManager.findFileTypeByName("IDEA_PROJECT");
    assertNotNull(project);
    assertFalse(project.equals(PlainTextFileType.INSTANCE));

    final Set<VirtualFile> detectorCalled = ContainerUtil.newConcurrentSet();
    FileTypeRegistry.FileTypeDetector detector = new FileTypeRegistry.FileTypeDetector() {
      @Nullable
      @Override
      public FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText) {
        detectorCalled.add(file);
        String text = firstCharsIfText.toString();
        FileType result = text.startsWith("TYPE:") ? fileTypeManager.findFileTypeByName(StringUtil.trimStart(text, "TYPE:")) : null;
        log("T: my detector run for "+file.getName()+"; result: "+(result == null ? null : result.getName())+" (text="+text+")");
        return result;
      }

      @Override
      public int getVersion() {
        return 0;
      }
    };
    FileTypeRegistry.FileTypeDetector.EP_NAME.getPoint(null).registerExtension(detector, getTestRootDisposable());
    myFileTypeManager.toLog = true;

    try {
      log("T: ------ akjdhfksdjgf");
      File f = createTempFile("xx.asfdasdfas", "akjdhfksdjgf");
      VirtualFile vFile = getVirtualFile(f);
      ensureRedetected(vFile, detectorCalled);
      assertTrue(vFile.getFileType().toString(), vFile.getFileType() instanceof PlainTextFileType);

      log("T: ------ TYPE:IDEA_MODULE");
      setFileText(vFile,  "TYPE:IDEA_MODULE");
      ensureRedetected(vFile, detectorCalled);
      assertTrue(vFile.getFileType().toString(), vFile.getFileType() instanceof ModuleFileType);

      log("T: ------ TYPE:IDEA_PROJECT");
      setFileText(vFile, "TYPE:IDEA_PROJECT");
      ensureRedetected(vFile, detectorCalled);
      assertTrue(vFile.getFileType().toString(), vFile.getFileType() instanceof ProjectFileType);
      log("T: ------");
    }
    finally {
      myFileTypeManager.toLog = false;
    }
  }

  private static void log(String message) {
    LOG.debug(message);
    //System.out.println(message);
  }

  private void ensureRedetected(VirtualFile vFile, Set<VirtualFile> detectorCalled) {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    log("T: ensureRedetected: commit. re-detect queue: "+myFileTypeManager.dumpReDetectQueue());
    UIUtil.dispatchAllInvocationEvents();
    log("T: ensureRedetected: dispatch. re-detect queue: "+ myFileTypeManager.dumpReDetectQueue());
    myFileTypeManager.drainReDetectQueue();
    log("T: ensureRedetected: drain. re-detect queue: "+myFileTypeManager.dumpReDetectQueue());
    UIUtil.dispatchAllInvocationEvents();
    log("T: ensureRedetected: dispatch. re-detect queue: "+myFileTypeManager.dumpReDetectQueue());
    FileType type = vFile.getFileType();
    log("T: ensureRedetected: getFileType ("+type.getName()+") re-detect queue: "+myFileTypeManager.dumpReDetectQueue());
    assertTrue(detectorCalled.contains(vFile));
    detectorCalled.clear();
    log("T: ensureRedetected: clear");
  }

  public void testReassignTextFileType() {
    doReassignTest(PlainTextFileType.INSTANCE, "dtd");
  }

  private void doReassignTest(FileType fileType, String extension) {
    try {
      ApplicationManager.getApplication().runWriteAction(() -> myFileTypeManager.associatePattern(fileType, "*." + extension));

      assertEquals(fileType, myFileTypeManager.getFileTypeByFileName("foo." + extension));

      Element element = myFileTypeManager.getState();

      myFileTypeManager.initStandardFileTypes();
      myFileTypeManager.loadState(element);
      myFileTypeManager.initializeComponent();

      assertEquals(fileType, myFileTypeManager.getFileTypeByFileName("foo." + extension));
    }
    finally {
      ApplicationManager.getApplication().runWriteAction(() -> myFileTypeManager.removeAssociatedExtension(fileType, "*." + extension));
    }
  }

  public void testRemovedMappingsSerialization() {
    HashSet<FileType> fileTypes = new HashSet<>(Arrays.asList(myFileTypeManager.getRegisteredFileTypes()));
    FileTypeAssocTable<FileType> table = myFileTypeManager.getExtensionMap().copy();

    ArchiveFileType fileType = ArchiveFileType.INSTANCE;
    FileNameMatcher matcher = table.getAssociations(fileType).get(0);

    table.removeAssociation(matcher, fileType);

    WriteAction.run(() -> myFileTypeManager.setPatternsTable(fileTypes, table));
    myFileTypeManager.getRemovedMappingTracker().add(matcher, fileType.getName(), true);

    Element state = myFileTypeManager.getState();

    myFileTypeManager.getRemovedMappingTracker().clear();
    myFileTypeManager.initStandardFileTypes();
    myFileTypeManager.loadState(state);
    myFileTypeManager.initializeComponent();

    List<RemovedMappingTracker.RemovedMapping> mappings = myFileTypeManager.getRemovedMappingTracker().getRemovedMappings();
    assertEquals(1, mappings.size());
    assertTrue(mappings.get(0).isApproved());
    assertEquals(matcher, mappings.get(0).getFileNameMatcher());
  }

  public void testRemovedExactNameMapping() {
    HashSet<FileType> fileTypes = new HashSet<>(Arrays.asList(myFileTypeManager.getRegisteredFileTypes()));
    FileTypeAssocTable<FileType> table = myFileTypeManager.getExtensionMap().copy();

    FileType fileType = ArchiveFileType.INSTANCE;
    ExactFileNameMatcher matcher = new ExactFileNameMatcher("foo.bar");
    table.addAssociation(matcher, fileType);

    table.removeAssociation(matcher, fileType);

    WriteAction.run(() -> myFileTypeManager.setPatternsTable(fileTypes, table));
    myFileTypeManager.getRemovedMappingTracker().add(matcher, fileType.getName(), true);

    Element state = myFileTypeManager.getState();

    myFileTypeManager.getRemovedMappingTracker().clear();
    myFileTypeManager.initStandardFileTypes();
    myFileTypeManager.loadState(state);
    myFileTypeManager.initializeComponent();

    List<RemovedMappingTracker.RemovedMapping> mappings = myFileTypeManager.getRemovedMappingTracker().getRemovedMappings();
    assertTrue(mappings.get(0).isApproved());
    assertEquals(matcher, mappings.get(0).getFileNameMatcher());
  }

  public void testReassignedPredefinedFileType() {
    final FileType fileType = myFileTypeManager.getFileTypeByFileName("foo.pl");
    assertEquals("Perl", fileType.getName());
    assertEquals(PlainTextFileType.INSTANCE, myFileTypeManager.getFileTypeByFileName("foo.cgi"));
    doReassignTest(fileType, "cgi");
  }

  public void testReAddedMapping() {
    ArchiveFileType fileType = ArchiveFileType.INSTANCE;
    FileNameMatcher matcher = myFileTypeManager.getAssociations(fileType).get(0);
    myFileTypeManager.getRemovedMappingTracker().add(matcher, fileType.getName(), true);

    WriteAction.run(() -> myFileTypeManager
      .setPatternsTable(new HashSet<>(Arrays.asList(myFileTypeManager.getRegisteredFileTypes())), myFileTypeManager.getExtensionMap().copy()));
    assertEquals(0, myFileTypeManager.getRemovedMappingTracker().getRemovedMappings().size());
  }

  public void testPreserveRemovedMappingForUnknownFileType() {
    myFileTypeManager.getRemovedMappingTracker().add(new ExtensionFileNameMatcher("xxx"), "Foo Files", true);
    WriteAction.run(() -> myFileTypeManager
      .setPatternsTable(new HashSet<>(Arrays.asList(myFileTypeManager.getRegisteredFileTypes())), myFileTypeManager.getExtensionMap().copy()));
    assertEquals(1, myFileTypeManager.getRemovedMappingTracker().getRemovedMappings().size());
  }

  public void testGetRemovedMappings() {
    FileTypeAssocTable<FileType> table = myFileTypeManager.getExtensionMap().copy();
    ArchiveFileType fileType = ArchiveFileType.INSTANCE;
    FileNameMatcher matcher = table.getAssociations(fileType).get(0);
    table.removeAssociation(matcher, fileType);
    Map<FileNameMatcher, FileType> reassigned =
      myFileTypeManager.getExtensionMap().getRemovedMappings(table, Arrays.asList(myFileTypeManager.getRegisteredFileTypes()));
    assertEquals(1, reassigned.size());
  }

  public void testRenamedXmlToUnknownAndBack() throws Exception {
    FileType propFileType = myFileTypeManager.getFileTypeByFileName("xx.xml");
    assertEquals("XML", propFileType.getName());
    File file = createTempFile("xx.xml", "<foo></foo>");
    VirtualFile vFile = getVirtualFile(file);
    assertEquals(propFileType, myFileTypeManager.getFileTypeByFile(vFile));

    rename(vFile, "xx.zxmcnbzmxnbc");
    UIUtil.dispatchAllInvocationEvents();
    assertEquals(PlainTextFileType.INSTANCE, myFileTypeManager.getFileTypeByFile(vFile));

    rename(vFile, "xx.xml");
    myFileTypeManager.drainReDetectQueue();
    for (int i=0; i<100;i++) {
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      UIUtil.dispatchAllInvocationEvents();

      assertEquals(propFileType, myFileTypeManager.getFileTypeByFile(vFile));
      assertEmpty(myFileTypeManager.dumpReDetectQueue());
    }
  }

  // for IDEA-114804 File types mapped to text are not remapped when corresponding plugin is installed
  public void testRemappingToInstalledPluginExtension() throws WriteExternalException, InvalidDataException {
    ApplicationManager.getApplication().runWriteAction(() -> myFileTypeManager.associatePattern(PlainTextFileType.INSTANCE, "*.fromPlugin"));

    Element element = myFileTypeManager.getState();
    //String s = JDOMUtil.writeElement(element);

    final AbstractFileType typeFromPlugin = new AbstractFileType(new SyntaxTable());
    FileTypeFactory.FILE_TYPE_FACTORY_EP.getPoint(null).registerExtension(new FileTypeFactory() {
      @Override
      public void createFileTypes(@NotNull FileTypeConsumer consumer) {
        consumer.consume(typeFromPlugin, "fromPlugin");
      }
    }, getTestRootDisposable());
    myFileTypeManager.initStandardFileTypes();
    myFileTypeManager.loadState(element);

    myFileTypeManager.initializeComponent();
    List<RemovedMappingTracker.RemovedMapping> mappings = myFileTypeManager.getRemovedMappingTracker().getRemovedMappings();
    assertEquals(1, mappings.size());
    assertEquals(typeFromPlugin.getName(), mappings.get(0).getFileTypeName());
  }

  public void testPreserveUninstalledPluginAssociations() {
    final FileType typeFromPlugin = createTestFileType();
    FileTypeFactory factory = new FileTypeFactory() {
      @Override
      public void createFileTypes(@NotNull FileTypeConsumer consumer) {
        consumer.consume(typeFromPlugin);
      }
    };
    Element element = myFileTypeManager.getState();
    Disposable disposable = Disposer.newDisposable();
    try {
      myFileTypeManager.clearForTests();
      FileTypeFactory.FILE_TYPE_FACTORY_EP.getPoint(null).registerExtension(factory, disposable);
      myFileTypeManager.initStandardFileTypes();
      myFileTypeManager.loadState(element);
      myFileTypeManager.initializeComponent();

      ApplicationManager.getApplication().runWriteAction(() -> myFileTypeManager.associatePattern(typeFromPlugin, "*.foo"));


      element = myFileTypeManager.getState();
      //log(JDOMUtil.writeElement(element));

      Disposer.dispose(disposable);
      disposable = null;
      myFileTypeManager.clearForTests();
      myFileTypeManager.initStandardFileTypes();
      myFileTypeManager.loadState(element);
      myFileTypeManager.initializeComponent();

      element = myFileTypeManager.getState();
      //log(JDOMUtil.writeElement(element));

      disposable = Disposer.newDisposable();
      FileTypeFactory.FILE_TYPE_FACTORY_EP.getPoint(null).registerExtension(factory, disposable);
      myFileTypeManager.clearForTests();
      myFileTypeManager.initStandardFileTypes();
      myFileTypeManager.loadState(element);
      myFileTypeManager.initializeComponent();

      //element = myFileTypeManager.getState();
      //log(JDOMUtil.writeElement(element));

      assertEquals(typeFromPlugin, myFileTypeManager.getFileTypeByFileName("foo.foo"));
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  // IDEA-139409 Persistent message "File type recognized: File extension *.vm was reassigned to VTL"
  public void testReassign() throws Exception {
    myFileTypeManager.clearForTests();
    myFileTypeManager.initStandardFileTypes();
    myFileTypeManager.initializeComponent();

    Element element = JDOMUtil.load(
      "<component name=\"FileTypeManager\" version=\"13\">\n" +
      "   <extensionMap>\n" +
      "      <mapping ext=\"zip\" type=\"Velocity Template files\" />\n" +
      "   </extensionMap>\n" +
      "</component>");

    myFileTypeManager.loadState(element);
    myFileTypeManager.initializeComponent();
    List<RemovedMappingTracker.RemovedMapping> mappings = myFileTypeManager.getRemovedMappingTracker().getRemovedMappings();
    assertEquals(1, mappings.size());
    assertEquals(ArchiveFileType.INSTANCE.getName(), mappings.get(0).getFileTypeName());
    myFileTypeManager.getRemovedMappingTracker().clear();
    assertEquals(ArchiveFileType.INSTANCE, myFileTypeManager.getFileTypeByExtension("zip"));
    Element map = myFileTypeManager.getState().getChild("extensionMap");
    if (map != null) {
      List<Element> mapping = map.getChildren("mapping");
      assertNull(ContainerUtil.find(mapping, o -> "zip".equals(o.getAttributeValue("ext"))));
    }
  }

  public void testDefaultFileType() {
    final String extension = "veryRareExtension";
    final FileType idl = myFileTypeManager.findFileTypeByName("IDL");
    ApplicationManager.getApplication().runWriteAction(() -> myFileTypeManager.associatePattern(idl, "*." + extension));

    Element element = myFileTypeManager.getState();
    //log(JDOMUtil.writeElement(element));
    ApplicationManager.getApplication().runWriteAction(() -> myFileTypeManager.removeAssociatedExtension(idl, extension));

    myFileTypeManager.clearForTests();
    myFileTypeManager.initStandardFileTypes();
    myFileTypeManager.loadState(element);
    myFileTypeManager.initializeComponent();
    FileType extensions = myFileTypeManager.getFileTypeByExtension(extension);
    assertEquals("IDL", extensions.getName());
    ApplicationManager.getApplication().runWriteAction(() -> myFileTypeManager.removeAssociatedExtension(idl, extension));
  }

  public void testIfDetectorRanThenIdeaReopenedTheDetectorShouldBeReRun() throws IOException {
    final UserBinaryFileType stuffType = new UserBinaryFileType();
    stuffType.setName("stuffType");

    final Set<VirtualFile> detectorCalled = ContainerUtil.newConcurrentSet();

    FileTypeRegistry.FileTypeDetector detector = new FileTypeRegistry.FileTypeDetector() {
      @Nullable
      @Override
      public FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText) {
        detectorCalled.add(file);
        FileType result = FileUtil.isHashBangLine(firstCharsIfText, "stuff") ? stuffType : null;
        log("T: my detector for file "+file.getName()+" run. result="+(result == null ? null : result.getName()));
        return result;
      }

      @Override
      public int getVersion() {
        return 0;
      }
    };
    FileTypeRegistry.FileTypeDetector.EP_NAME.getPoint(null).registerExtension(detector, getTestRootDisposable());
    myFileTypeManager.toLog = true;

    try {
      log("T: ------ akjdhfksdjgf");
      File f = createTempFile("xx.asfdasdfas", "akjdhfksdjgf");
      VirtualFile file = getVirtualFile(f);
      ensureRedetected(file, detectorCalled);
      assertTrue(file.getFileType().toString(), file.getFileType() instanceof PlainTextFileType);

      log("T: ------ my");
      setFileText(file,  "#!stuff\nxx");
      ensureRedetected(file, detectorCalled);
      assertEquals(stuffType, file.getFileType());

      log("T: ------ reload");
      myFileTypeManager.drainReDetectQueue();
      myFileTypeManager.clearCaches();
      file.putUserData(FileTypeManagerImpl.DETECTED_FROM_CONTENT_FILE_TYPE_KEY, null);

      ensureRedetected(file, detectorCalled);
      assertSame(file.getFileType().toString(), file.getFileType(), stuffType);
      log("T: ------");
    }
    finally {
      myFileTypeManager.toLog = false;
    }
  }

  public void _testStressPlainTextFileWithEverIncreasingLength() throws IOException {
    FrequentEventDetector.disableUntil(getTestRootDisposable());

    File f = createTempFile("xx.lkjlkjlkjlj", "a");
    VirtualFile virtualFile = getVirtualFile(f);
    assertEquals(PlainTextFileType.INSTANCE, virtualFile.getFileType());
    //PsiFile psiFile = getPsiManager().findFile(virtualFile);
    //assertTrue(psiFile instanceof PsiPlainTextFile);

    int NThreads = 8;
    int N = 1000;
    Random random = new Random();
    AtomicReference<Exception> exception = new AtomicReference<>();
    List<Thread> threads = ContainerUtil.map(new Object[NThreads], o -> new Thread(() -> {
      try {
        for (int i = 0; i < N; i++) {
          boolean isText = ReadAction.compute(() -> {
            if (virtualFile.getFileType().isBinary()) {
              return false;
            }
            else {
              LoadTextUtil.loadText(virtualFile);
              return true;
            }
          });

          if (random.nextInt(3) == 0) {
            WriteCommandAction.writeCommandAction(getProject()).run(() -> {
              byte[] bytes = new byte[(int)PersistentFSConstants.FILE_LENGTH_TO_CACHE_THRESHOLD + (isText ? 1 : 0)];
              Arrays.fill(bytes, (byte)' ');
              virtualFile.setBinaryContent(bytes);
            });


            //RandomAccessFile ra = new RandomAccessFile(f, "rw");
            //ra.setLength(ra.length()+(isText ? 1 : -1));
            //ra.close();
            //LocalFileSystem.getInstance().refreshFiles(Collections.singletonList(virtualFile));
            LOG.debug(i + "; f = " + f.length() + "; virtualFile=" + virtualFile.getLength() + "; type=" + virtualFile.getFileType());
            //Thread.sleep(random.nextInt(100));
          }
        }
      }
      catch (Exception e) {
        exception.set(e);
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    }, "reader"));
    threads.forEach(Thread::start);
    for (Thread thread : threads) {
      while (thread.isAlive()) {
        if (exception.get() != null) throw new RuntimeException(exception.get());
        UIUtil.dispatchAllInvocationEvents(); //refresh
      }
    }
    if (exception.get() != null) throw new RuntimeException(exception.get());
  }

  public void _testStressPlainTextFileWithEverIncreasingLength2() throws IOException {
    FrequentEventDetector.disableUntil(getTestRootDisposable());

    File f = createTempFile("xx.asdkjfhlkasjdhf", StringUtil.repeatSymbol(' ', (int)PersistentFSConstants.FILE_LENGTH_TO_CACHE_THRESHOLD - 100));
    VirtualFile virtualFile = getVirtualFile(f);
    assertEquals(PlainTextFileType.INSTANCE, virtualFile.getFileType());
    PsiFile psiFile = getPsiManager().findFile(virtualFile);
    assertTrue(psiFile instanceof PsiPlainTextFile);

    int NThreads = 1;
    int N = 10;
    List<Thread> threads = ContainerUtil.map(new Object[NThreads], o -> new Thread(() -> {
      for (int i = 0; i < N; i++) {
        ApplicationManager.getApplication().runReadAction(() -> {
          String text = psiFile.getText();
          LOG.debug("text = " + text.length());
          //if (!virtualFile.getFileType().isBinary()) {
          //  LoadTextUtil.loadText(virtualFile);
          //}
        });
        if (i % 1 == 0) {
          try {
            FileUtil.appendToFile(f, StringUtil.repeatSymbol(' ', 50));
            LocalFileSystem.getInstance().refreshFiles(Collections.singletonList(virtualFile));
            LOG.debug("f = " + f.length()+"; virtualFile="+virtualFile.getLength()+"; psiFile="+psiFile.isValid()+"; type="+virtualFile.getFileType());
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      }
    }, "reader"));
    threads.forEach(Thread::start);
    for (Thread thread : threads) {
      while (thread.isAlive()) {
        UIUtil.dispatchAllInvocationEvents(); //refresh
      }
    }
  }

  public void testChangeEncodingManuallyForAutoDetectedFileSticks() throws IOException {
    EncodingProjectManagerImpl manager = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject());
    String oldProject = manager.getDefaultCharsetName();
    try {
      VirtualFile file = createTempFile("sldkfjlskdfj", null, "123456789", StandardCharsets.UTF_8);
      manager.setEncoding(file, CharsetToolkit.WIN_1251_CHARSET);
      file.setCharset(CharsetToolkit.WIN_1251_CHARSET);
      UIUtil.dispatchAllInvocationEvents();
      ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue();
      UIUtil.dispatchAllInvocationEvents();

      assertEquals(PlainTextFileType.INSTANCE, file.getFileType());

      manager.setEncoding(file, CharsetToolkit.US_ASCII_CHARSET);
      UIUtil.dispatchAllInvocationEvents();
      ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue();
      UIUtil.dispatchAllInvocationEvents();
      assertEquals(CharsetToolkit.US_ASCII_CHARSET, file.getCharset());

      manager.setEncoding(file, StandardCharsets.UTF_8);
      UIUtil.dispatchAllInvocationEvents();
      ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue();
      UIUtil.dispatchAllInvocationEvents();
      assertEquals(StandardCharsets.UTF_8, file.getCharset());

      manager.setEncoding(file, CharsetToolkit.US_ASCII_CHARSET);
      UIUtil.dispatchAllInvocationEvents();
      ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue();
      UIUtil.dispatchAllInvocationEvents();
      assertEquals(CharsetToolkit.US_ASCII_CHARSET, file.getCharset());

      manager.setEncoding(file, StandardCharsets.UTF_8);
      UIUtil.dispatchAllInvocationEvents();
      ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue();
      UIUtil.dispatchAllInvocationEvents();
      assertEquals(StandardCharsets.UTF_8, file.getCharset());
    }
    finally {
      manager.setDefaultCharsetName(oldProject);
    }
  }

  public void testFileTypeBeanByName() {
    assertInstanceOf(myFileTypeManager.getStdFileType("IDEA_WORKSPACE"), WorkspaceFileType.class);
  }

  public void testFileTypeBeanRegistered() {
    final FileType[] types = myFileTypeManager.getRegisteredFileTypes();
    assertTrue(ContainerUtil.exists(types, (it) -> it instanceof WorkspaceFileType));
  }

  public void testFileTypeBeanByFileName() {
    assertInstanceOf(myFileTypeManager.getFileTypeByFileName("foo.iws"), WorkspaceFileType.class);
  }

  public void testFileTypeBeanByExtensionWithFieldName() {
    assertSame(ModuleFileType.INSTANCE, myFileTypeManager.getFileTypeByExtension("iml"));
  }

  @NotNull
  private static FileType createTestFileType() {
    return new FileType() {
      @NotNull
      @Override
      public String getName() {
        return "Foo files";
      }

      @NotNull
      @Override
      public String getDescription() {
        return "";
      }

      @NotNull
      @Override
      public String getDefaultExtension() {
        return "fromPlugin";
      }

      @Nullable
      @Override
      public Icon getIcon() {
        return null;
      }

      @Override
      public boolean isBinary() {
        return false;
      }

      @Override
      public boolean isReadOnly() {
        return false;
      }

      @Nullable
      @Override
      public String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
        return null;
      }
    };
  }
}
