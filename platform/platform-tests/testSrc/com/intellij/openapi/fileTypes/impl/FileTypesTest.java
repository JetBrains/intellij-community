// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.highlighter.WorkspaceFileType;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginDescriptorTestKt;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.FrequentEventDetector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.fileTypes.impl.ConflictingFileTypeMappingTracker.ResolveConflictResult;
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
import com.intellij.openapi.vfs.newvfs.impl.CachedFileType;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PatternUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assume;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FileTypesTest extends HeavyPlatformTestCase {
  private static final Logger LOG = Logger.getInstance(FileTypesTest.class);

  private List<ResolveConflictResult> myConflicts;
  private FileTypeManagerImpl myFileTypeManager;
  private Element myGlobalStateBefore;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // we test against myFileTypeManager instance only, standard FileTypeManager.getInstance() must not be changed in any way
    myFileTypeManager = new FileTypeManagerImpl();
    myFileTypeManager.listenAsyncVfsEvents();
    myFileTypeManager.initializeComponent();
    myFileTypeManager.getRegisteredFileTypes();
    myFileTypeManager.reDetectAsync(true);
    Assume.assumeTrue(
      "This test must be run under community classpath because otherwise everything would break thanks to weird HelmYamlLanguage" +
      " which is created on each HelmYamlFileType registration which happens a lot in these tests",
      PlatformTestUtil.isUnderCommunityClassPath());
    myConflicts = new ArrayList<>();
    myFileTypeManager.setConflictResultConsumer(myConflicts::add);
    myGlobalStateBefore = ((FileTypeManagerImpl)FileTypeManagerEx.getInstanceEx()).getState();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFileTypeManager.setConflictResultConsumer(null);
      myConflicts = null;
      assertFileTypeIsUnregistered(new MyCustomImageFileType());
      assertFileTypeIsUnregistered(new MyCustomImageFileType2());
      assertFileTypeIsUnregistered(new MyTestFileType());
      assertFileTypeIsUnregistered(new MyHaskellFileType());
      assertEmpty(myFileTypeManager.getRemovedMappingTracker().getRemovedMappings());
      myFileTypeManager.reDetectAsync(false);
      assertNull(myFileTypeManager.findFileTypeByName("x." + MyTestFileType.EXTENSION));
      assertNull(myFileTypeManager.getExtensionMap().findByExtension(MyTestFileType.EXTENSION));
      Disposer.dispose(myFileTypeManager);
      Element globalStateAfter = ((FileTypeManagerImpl)FileTypeManagerEx.getInstanceEx()).getState();
      assertEquals(JDOMUtil.writeElement(myGlobalStateBefore), JDOMUtil.writeElement(globalStateAfter));
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myFileTypeManager = null;
      myGlobalStateBefore = null;
      super.tearDown();
    }
  }

  public void testMaskExclude() {
    String pattern1 = "a*b.c?d";
    String pattern2 = "xxx";
    WriteAction.run(() -> myFileTypeManager.setIgnoredFilesList(pattern1 + ";" + pattern2));
    checkIgnored("ab.cxd");
    checkIgnored("axb.cxd");
    checkIgnored("xxx");
    checkNotIgnored("ax.cxx");
    checkNotIgnored("ab.cd");
    checkNotIgnored("ab.c__d");
    checkNotIgnored(pattern2 + 'x');
    checkNotIgnored("xx");
    assertTrue(myFileTypeManager.isIgnoredFilesListEqualToCurrent(pattern2 + ";" + pattern1));
    assertFalse(myFileTypeManager.isIgnoredFilesListEqualToCurrent(pattern2 + ";" + "ab.c*d"));
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
    FileType XML = StdFileTypes.XML;
    FileTypeAssocTable<FileType> associations = new FileTypeAssocTable<>();
    associations.addAssociation(FileTypeManager.parseFromString("*.java"), ArchiveFileType.INSTANCE);
    associations.addAssociation(FileTypeManager.parseFromString("*.xyz"), XML);
    associations.addAssociation(FileTypeManager.parseFromString("SomeSpecial*.java"), XML); // patterns should have precedence over extensions
    assertEquals(XML, associations.findAssociatedFileType("sample.xyz"));
    assertEquals(XML, associations.findAssociatedFileType("SomeSpecialFile.java"));
    checkNotAssociated(XML, "java", associations);
    checkNotAssociated(XML, "iws", associations);
  }

  public void testIgnoreOrder() {
    WriteAction.run(() -> myFileTypeManager.setIgnoredFilesList("a;b"));
    assertEquals("a;b", myFileTypeManager.getIgnoredFilesList());
    WriteAction.run(() -> myFileTypeManager.setIgnoredFilesList("b;a"));
    assertEquals("b;a", myFileTypeManager.getIgnoredFilesList());
  }

  public void testIgnoredFiles() throws IOException {
    VirtualFile file = getVirtualFile(createTempFile(".svn", ""));
    assertTrue(myFileTypeManager.isFileIgnored(file));
    assertFalse(myFileTypeManager.isFileIgnored(createTempVirtualFile("x.txt", null, "", StandardCharsets.UTF_8)));
  }

  public void testEmptyFileWithoutExtension() throws IOException {
    VirtualFile foo = getVirtualFile(createTempFile("foo", ""));
    WriteAction.run(() -> myFileTypeManager.associatePattern(DetectedByContentFileType.INSTANCE, "foo"));
    FileType type = myFileTypeManager.getFileTypeByFile(foo); // foo.getFileType() will call FileTypeRegistry.getInstance() which we try to avoid
    assertFalse(type.getName(), type.isBinary());
  }

  private static void checkNotAssociated(@NotNull FileType fileType,
                                         @NotNull String extension,
                                         @NotNull FileTypeAssocTable<FileType> associations) {
    assertFalse(ArrayUtil.contains(extension, associations.getAssociatedExtensions(fileType)));
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
    assertTrue(String.valueOf(psi), psi instanceof PsiPlainTextFile);
    assertEquals(FileTypes.PLAIN_TEXT, getFileType(virtualFile));
  }

  public void testAutoDetectedWhenDocumentWasCreated() throws IOException {
    File dir = createTempDirectory();
    File file = FileUtil.createTempFile(dir, "x", "xxx_xx_xx", true);
    FileUtil.writeToFile(file, "xxx xxx xxx xxx");
    VirtualFile virtualFile = getVirtualFile(file);
    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    assertNotNull(document);
    assertEquals(FileTypes.PLAIN_TEXT, getFileType(virtualFile));
  }

  public void testAutoDetectionShouldNotBeOverEager() throws IOException {
    File dir = createTempDirectory();
    File file = FileUtil.createTempFile(dir, "x", "xxx_xx_xx", true);
    FileUtil.writeToFile(file, "xxx xxx xxx xxx");
    VirtualFile virtualFile = getVirtualFile(file);
    FileType fileType = getFileType(virtualFile);

    assertEquals(myFileTypeManager.getRemovedMappingTracker().getRemovedMappings().toString(), PlainTextFileType.INSTANCE, fileType);
  }

  public void testAutoDetectEmptyFile() throws IOException {
    File dir = createTempDirectory();
    File file = FileUtil.createTempFile(dir, "x", "xxx_xx_xx", true);
    VirtualFile virtualFile = getVirtualFile(file);
    Assume.assumeTrue(UnknownFileType.INSTANCE.equals(myFileTypeManager.getFileTypeByFileName(virtualFile.getName())));
    assertEquals(DetectedByContentFileType.INSTANCE, getFileType(virtualFile));
    PsiFile psi = getPsiManager().findFile(virtualFile);
    assertFalse(psi.toString(), psi instanceof PsiBinaryFile);
    assertEquals(DetectedByContentFileType.INSTANCE, getFileType(virtualFile));

    setBinaryContent(virtualFile, "x_x_x_x".getBytes(StandardCharsets.UTF_8));
    assertEquals(FileTypes.PLAIN_TEXT, getFileType(virtualFile));
    PsiFile after = getPsiManager().findFile(virtualFile);
    assertNotSame(psi, after);
    assertFalse(psi.isValid());
    assertTrue(after.isValid());
    assertTrue(after instanceof PsiPlainTextFile);
  }

  public void testAutoDetectTextFileFromContents() throws IOException {
    File dir = createTempDirectory();
    VirtualFile vDir = getVirtualFile(dir);
    VirtualFile vFile = createChildData(vDir, "test.x_x_x_x");
    setFileText(vFile, "text");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    assertEquals(PlainTextFileType.INSTANCE, getFileType(vFile)); // type is autodetected during indexing

    PsiFile psiFile = PsiManagerEx.getInstanceEx(getProject()).getFileManager().findFile(vFile); // autodetect text file if needed
    assertNotNull(psiFile);
    assertEquals(PlainTextFileType.INSTANCE, psiFile.getFileType());
  }

  public void testAutoDetectTextFileEvenOutsideTheProject() throws IOException {
    File d = createTempDirectory();
    File f = new File(d, "xx.asf_das_dfa");
    FileUtil.writeToFile(f, "asd_asd_asd_faf_dsa");
    VirtualFile vFile = getVirtualFile(f);

    assertEquals(PlainTextFileType.INSTANCE, getFileType(vFile));
  }

  public void test7BitBinaryIsNotText() throws IOException {
    File d = createTempDirectory();
    byte[] bytes = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 'x', 'a', 'b'};
    assertEquals(CharsetToolkit.GuessedEncoding.BINARY,
                 new CharsetToolkit(bytes, Charset.defaultCharset(), false).guessFromContent(bytes.length));
    File f = new File(d, "xx.asf_das_dfa");
    FileUtil.writeToFile(f, bytes);

    VirtualFile vFile = getVirtualFile(f);

    assertEquals(UnknownFileType.INSTANCE, getFileType(vFile));
  }

  public void test7BitIsText() throws IOException {
    File d = createTempDirectory();
    byte[] bytes = {9, 10, 13, 'x', 'a', 'b'};
    assertEquals(CharsetToolkit.GuessedEncoding.SEVEN_BIT,
                 new CharsetToolkit(bytes, Charset.defaultCharset(), false).guessFromContent(bytes.length));
    File f = new File(d, "xx.asf_das_dfa");
    FileUtil.writeToFile(f, bytes);
    VirtualFile vFile = getVirtualFile(f);

    assertEquals(PlainTextFileType.INSTANCE, getFileType(vFile));
  }

  public void testReDetectOnContentChange() throws IOException {
    FileType fileType = myFileTypeManager.getFileTypeByFileName("x" + ModuleFileType.DOT_DEFAULT_EXTENSION);
    assertTrue(fileType.toString(), fileType instanceof ModuleFileType);
    fileType = myFileTypeManager.getFileTypeByFileName("x" + ProjectFileType.DOT_DEFAULT_EXTENSION);
    assertTrue(fileType.toString(), fileType instanceof ProjectFileType);
    FileType module = myFileTypeManager.findFileTypeByName("IDEA_MODULE");
    assertNotNull(module);
    assertFalse(module.equals(PlainTextFileType.INSTANCE));
    FileType project = myFileTypeManager.findFileTypeByName("IDEA_PROJECT");
    assertNotNull(project);
    assertFalse(project.equals(PlainTextFileType.INSTANCE));

    Set<VirtualFile> detectorCalled = ContainerUtil.newConcurrentSet();
    FileTypeRegistry.FileTypeDetector detector = new FileTypeRegistry.FileTypeDetector() {
      @Override
      public @Nullable FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText) {
        detectorCalled.add(file);
        String text = firstCharsIfText != null ? firstCharsIfText.toString() : null;
        FileType result = text != null && text.startsWith("TYPE:")
                          ? myFileTypeManager.findFileTypeByName(StringUtil.trimStart(text, "TYPE:"))
                          : null;
        log("T: my detector run for "+file.getName()+"; result: "+(result == null ? null : result.getName())+" (text="+text+")");
        return result;
      }

      @Override
      public int getDesiredContentPrefixLength() {
        return 48;
      }
    };
    runWithDetector(detector, () -> {
      log("T: ------ akj_dhf_ksd_jgf");
      File f = createTempFile("xx.asf_das_dfs", "akj_dhf_ksd_jgf");
      VirtualFile vFile = getVirtualFile(f);
      ensureReDetected(vFile, detectorCalled);
      assertTrue(getFileType(vFile).toString(), getFileType(vFile) instanceof PlainTextFileType);

      log("T: ------ TYPE:IDEA_MODULE");
      setFileText(vFile,  "TYPE:IDEA_MODULE");
      ensureReDetected(vFile, detectorCalled);
      assertTrue(getFileType(vFile).toString(), getFileType(vFile) instanceof ModuleFileType);

      log("T: ------ TYPE:IDEA_PROJECT");
      setFileText(vFile, "TYPE:IDEA_PROJECT");
      ensureReDetected(vFile, detectorCalled);
      assertTrue(getFileType(vFile).toString(), getFileType(vFile) instanceof ProjectFileType);
      log("T: ------");
    });
  }

  private <T extends Throwable> void runWithDetector(@NotNull FileTypeRegistry.FileTypeDetector detector, @NotNull ThrowableRunnable<T> runnable) throws T {
    Disposable disposable = Disposer.newDisposable();
    FileTypeRegistry.FileTypeDetector.EP_NAME.getPoint().registerExtension(detector, disposable);
    FileTypeManagerImpl fileTypeManager = myFileTypeManager;
    fileTypeManager.toLog = true;
    try {
      runnable.run();
    }
    finally {
      fileTypeManager.toLog = false;
      Disposer.dispose(disposable);
    }
  }

  private static void log(String message) {
    LOG.debug(message);
    //System.out.println(message);
  }

  private void ensureReDetected(@NotNull VirtualFile vFile, @NotNull Set<VirtualFile> detectorCalled) {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    log("T: ensureReDetected: commit. re-detect queue: " + myFileTypeManager.dumpReDetectQueue());
    UIUtil.dispatchAllInvocationEvents();
    log("T: ensureReDetected: dispatch. re-detect queue: " + myFileTypeManager.dumpReDetectQueue());
    myFileTypeManager.drainReDetectQueue();
    log("T: ensureReDetected: drain. re-detect queue: " + myFileTypeManager.dumpReDetectQueue());
    UIUtil.dispatchAllInvocationEvents();
    log("T: ensureReDetected: dispatch. re-detect queue: " + myFileTypeManager.dumpReDetectQueue());
    FileType type = getFileType(vFile);
    log("T: ensureReDetected: getFileType (" + type.getName() + ") re-detect queue: " + myFileTypeManager.dumpReDetectQueue());
    assertTrue(detectorCalled.contains(vFile));
    detectorCalled.clear();
    log("T: ensureReDetected: clear");
  }

  public void testReassignTextFileType() {
    doReassignTest(PlainTextFileType.INSTANCE, "dtd");
  }

  private void doReassignTest(@NotNull FileType fileType, @NotNull String newExtension) {
    FileType oldFileType = myFileTypeManager.getFileTypeByExtension(newExtension);
    try {
      DefaultLogger.disableStderrDumping(getTestRootDisposable());
      myFileTypeManager.getRegisteredFileTypes(); // ensure pending file types empty

      assertNotSame(FileTypes.UNKNOWN, oldFileType);
      WriteAction.run(() -> myFileTypeManager.removeAssociatedExtension(fileType, newExtension));
      WriteAction.run(() -> myFileTypeManager.getRemovedMappingTracker().add(new ExtensionFileNameMatcher(newExtension), oldFileType.getName(), true));
      WriteAction.run(() -> myFileTypeManager.associateExtension(fileType, newExtension));

      assertEquals(fileType, myFileTypeManager.getFileTypeByFileName("foo." + newExtension));
    }
    finally {
      WriteAction.run(() -> {
        myFileTypeManager.removeAssociatedExtension(fileType, newExtension);
        myFileTypeManager.getRemovedMappingTracker().removeIf(mapping ->
                                                                mapping.getFileTypeName().equals(oldFileType.getName())
                                                                && mapping.getFileNameMatcher() instanceof ExtensionFileNameMatcher
                                                                && ((ExtensionFileNameMatcher)mapping.getFileNameMatcher()).getExtension().equals(newExtension));
        myFileTypeManager.associateExtension(oldFileType, newExtension);
      });
    }
    assertEquals(oldFileType, myFileTypeManager.getFileTypeByFileName("foo." + newExtension));
  }

  public void testRemovedMappingSerialization() {
    Set<FileTypeManagerImpl.FileTypeWithDescriptor> fileTypes = new HashSet<>(myFileTypeManager.getRegisteredFileTypeWithDescriptors());
    FileTypeAssocTable<FileTypeManagerImpl.FileTypeWithDescriptor> table = myFileTypeManager.getExtensionMap().copy();

    FileTypeManagerImpl.FileTypeWithDescriptor fileType = FileTypeManagerImpl.coreDescriptorFor(ArchiveFileType.INSTANCE);
    FileNameMatcher matcher = table.getAssociations(fileType).get(0);

    table.removeAssociation(matcher, fileType);

    WriteAction.run(() -> myFileTypeManager.setPatternsTable(fileTypes, table));
    myFileTypeManager.getRemovedMappingTracker().add(matcher, fileType.fileType.getName(), true);

    Element state = myFileTypeManager.getState();
    LOG.debug(JDOMUtil.writeElement(state));

    reInitFileTypeManagerComponent(state);

    List<RemovedMappingTracker.RemovedMapping> mappings = myFileTypeManager.getRemovedMappingTracker().getRemovedMappings();
    assertEquals(1, mappings.size());
    assertTrue(mappings.get(0).isApproved());
    assertEquals(matcher, mappings.get(0).getFileNameMatcher());
    myFileTypeManager.getRemovedMappingTracker().clear();
  }

  public void testRemovedExactNameMapping() {
    Set<FileTypeManagerImpl.FileTypeWithDescriptor> fileTypes = new HashSet<>(myFileTypeManager.getRegisteredFileTypeWithDescriptors());
    FileTypeAssocTable<FileTypeManagerImpl.FileTypeWithDescriptor> table = myFileTypeManager.getExtensionMap().copy();

    FileTypeManagerImpl.FileTypeWithDescriptor fileType = FileTypeManagerImpl.coreDescriptorFor(ArchiveFileType.INSTANCE);
    ExactFileNameMatcher matcher = new ExactFileNameMatcher("foo.bar");
    table.addAssociation(matcher, fileType);

    table.removeAssociation(matcher, fileType);

    WriteAction.run(() -> myFileTypeManager.setPatternsTable(fileTypes, table));
    myFileTypeManager.getRemovedMappingTracker().add(matcher, fileType.fileType.getName(), true);

    Element state = myFileTypeManager.getState();
    LOG.debug(JDOMUtil.writeElement(state));

    reInitFileTypeManagerComponent(state);

    List<RemovedMappingTracker.RemovedMapping> mappings = myFileTypeManager.getRemovedMappingTracker().getRemovedMappings();
    assertTrue(mappings.get(0).isApproved());
    assertEquals(matcher, mappings.get(0).getFileNameMatcher());
    assertOneElement(myFileTypeManager.getRemovedMappingTracker().removeIf(mapping -> mapping.getFileNameMatcher().equals(matcher)));
  }

  public void testAddExistingExtensionFromFileTypeXToFileTypeYMustSurviveRestart() throws IOException, JDOMException {
    String ext = ((ExtensionFileNameMatcher)Arrays.stream(myFileTypeManager.getRegisteredFileTypes())
      .filter(type -> !(type instanceof AbstractFileType))
      .flatMap(type -> myFileTypeManager.getAssociations(type).stream())
      .filter(association -> association instanceof ExtensionFileNameMatcher)
      .findFirst()
      .orElseThrow()).getExtension();

    FileType type = myFileTypeManager.getFileTypeByExtension(ext);
    FileType otherType = ContainerUtil.find(myFileTypeManager.getRegisteredFileTypes(), t -> !t.equals(type) && t instanceof AbstractFileType);
    // try to assign ext from type to otherType

    WriteAction.run(() -> myFileTypeManager.associateExtension(otherType, ext));
    assertEquals(otherType, myFileTypeManager.getFileTypeByExtension(ext));
    assertEmpty(myFileTypeManager.getRemovedMappingTracker().getMappingsForFileType(type.getName()));
    
    @Language("XML")
    String xml = "<blahblah version='" + FileTypeManagerImpl.VERSION + "'>\n" +
                 "   <extensionMap>\n" +
                 "     <mapping ext=\""+ext+"\" type=\"" + otherType.getName()+ "\" />\n" +
                 "     <removed_mapping ext=\""+ext+"\" type=\"" + type.getName()+ "\" approved=\"true\"/>\n" +
                 "   </extensionMap>\n" +
                 "</blahblah>";
    Element element = JDOMUtil.load(xml);

    myFileTypeManager.getRegisteredFileTypes(); // instantiate pending file types
    reInitFileTypeManagerComponent(element);
    assertEmpty(myConflicts);

    assertEquals(otherType, myFileTypeManager.getFileTypeByExtension(ext));
    assertNotEmpty(myFileTypeManager.getRemovedMappingTracker().getMappingsForFileType(type.getName()));
    myFileTypeManager.getRemovedMappingTracker().clear();
  }

  public void testAddHashBangToReassignedTypeMustSurviveRestart() throws IOException, JDOMException {
    FileTypeManagerImpl.FileTypeWithDescriptor ftd = ContainerUtil.find(myFileTypeManager.getRegisteredFileTypeWithDescriptors(),
      f -> !(f.fileType instanceof AbstractFileType));

    String hashBang = "xxx";
    @Language("XML")
    String xml = "<blahblah version='" + FileTypeManagerImpl.VERSION + "'>\n" +
                 "   <extensionMap>\n" +
                 "     <hashBang value=\"" + hashBang + "\" type=\"" + ftd.getName() + "\" />\n"+
                 "   </extensionMap>\n" +
                 "</blahblah>";
    Element element = JDOMUtil.load(xml);

    myFileTypeManager.getRegisteredFileTypes(); // instantiate pending file types
    reInitFileTypeManagerComponent(element);
    assertEmpty(myConflicts);

    assertEquals(ftd, myFileTypeManager.getExtensionMap().findAssociatedFileTypeByHashBang("#!" + hashBang+"\n"));
  }

  public void testReassignedPredefinedFileType() {
    FileType perlType = myFileTypeManager.getFileTypeByFileName("foo.pl");
    assertEquals("Perl", perlType.getName());
    assertEquals(PlainTextFileType.INSTANCE, myFileTypeManager.getFileTypeByFileName("foo.txt"));
    doReassignTest(perlType, "txt");
  }

  public void testReAddedMapping() {
    ArchiveFileType fileType = ArchiveFileType.INSTANCE;
    FileNameMatcher matcher = myFileTypeManager.getAssociations(fileType).get(0);
    myFileTypeManager.getRemovedMappingTracker().add(matcher, fileType.getName(), true);

    WriteAction.run(() -> myFileTypeManager.setPatternsTable(
      new HashSet<>(myFileTypeManager.getRegisteredFileTypeWithDescriptors()), myFileTypeManager.getExtensionMap().copy()));
    assertEmpty(myFileTypeManager.getRemovedMappingTracker().getRemovedMappings());
  }

  public void testPreserveRemovedMappingForUnknownFileType() {
    myFileTypeManager.getRemovedMappingTracker().add(new ExtensionFileNameMatcher("xxx"), MyTestFileType.NAME, true);
    WriteAction.run(() -> myFileTypeManager.setPatternsTable(
      new HashSet<>(myFileTypeManager.getRegisteredFileTypeWithDescriptors()), myFileTypeManager.getExtensionMap().copy()));
    assertEquals(1, myFileTypeManager.getRemovedMappingTracker().getRemovedMappings().size());
    myFileTypeManager.getRemovedMappingTracker().clear();
  }

  public void testGetRemovedMappings() {
    FileTypeAssocTable<FileTypeManagerImpl.FileTypeWithDescriptor> table = myFileTypeManager.getExtensionMap().copy();
    FileTypeManagerImpl.FileTypeWithDescriptor fileType = FileTypeManagerImpl.coreDescriptorFor(ArchiveFileType.INSTANCE);
    FileNameMatcher matcher = table.getAssociations(fileType).get(0);
    table.removeAssociation(matcher, fileType);
    Map<FileNameMatcher, FileTypeManagerImpl.FileTypeWithDescriptor> reassigned =
      myFileTypeManager.getExtensionMap().getRemovedMappings(table, myFileTypeManager.getRegisteredFileTypeWithDescriptors());
    assertEquals(1, reassigned.size());
  }

  public void testRenamedXmlToUnknownAndBack() throws Exception {
    FileType propFileType = myFileTypeManager.getFileTypeByFileName("xx.xml");
    assertEquals("XML", propFileType.getName());
    File file = createTempFile("xx.xml", "<foo></foo>");
    VirtualFile vFile = getVirtualFile(file);
    assertEquals(propFileType, myFileTypeManager.getFileTypeByFile(vFile));

    rename(vFile, "xx.zxm_cnb_zmx_nbc");
    UIUtil.dispatchAllInvocationEvents();
    assertEquals(PlainTextFileType.INSTANCE, myFileTypeManager.getFileTypeByFile(vFile));

    rename(vFile, "xx.xml");
    myFileTypeManager.drainReDetectQueue();
    for (int i = 0; i < 100; i++) {
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      UIUtil.dispatchAllInvocationEvents();

      assertEquals(propFileType, myFileTypeManager.getFileTypeByFile(vFile));
      assertEmpty(myFileTypeManager.dumpReDetectQueue());
    }
  }

  // IDEA-114804 "File types mapped to text are not remapped when a corresponding plugin is installed"
  public void testRemappingToInstalledPluginExtension() throws WriteExternalException, InvalidDataException {
    myFileTypeManager.getRegisteredFileTypes();
    WriteAction.run(() -> myFileTypeManager.associateExtension(PlainTextFileType.INSTANCE, MyTestFileType.EXTENSION));

    Element element = myFileTypeManager.getState();

    FileTypeBean bean = new FileTypeBean();
    bean.name = MyTestFileType.NAME;
    bean.implementationClass = MyTestFileType.class.getName();
    bean.extensions = MyTestFileType.EXTENSION;
    IdeaPluginDescriptorImpl pluginDescriptor =
      PluginDescriptorTestKt.readDescriptorForTest(Path.of(""), false, "<idea-plugin/>".getBytes(StandardCharsets.UTF_8), PluginId.getId("myPlugin"));
    Disposable disposable = registerFileType(bean, pluginDescriptor);
    try {
      reInitFileTypeManagerComponent(element);
      List<RemovedMappingTracker.RemovedMapping> mappings = myFileTypeManager.getRemovedMappingTracker().getRemovedMappings();
      assertEquals(1, mappings.size());
      assertEquals(MyTestFileType.NAME, mappings.get(0).getFileTypeName());
    }
    finally {
      WriteAction.run(() -> Disposer.dispose(disposable));
      WriteAction.run(() -> myFileTypeManager.removeAssociatedExtension(PlainTextFileType.INSTANCE, MyTestFileType.EXTENSION));
    }
  }

  private void reInitFileTypeManagerComponent(@Nullable Element element) {
    myFileTypeManager.getRemovedMappingTracker().clear();
    myFileTypeManager.clearStandardFileTypesBeforeTest();
    if (element != null) {
      myFileTypeManager.loadState(element);
    }
    myFileTypeManager.initializeComponent();
    myFileTypeManager.getRegisteredFileTypes();
  }

  public void testRegisterConflictingExtensionMustBeReported() throws WriteExternalException, InvalidDataException {
    String myWeirdExtension = "my_weird_extension";
    WriteAction.run(() -> myFileTypeManager.associateExtension(PlainTextFileType.INSTANCE, myWeirdExtension));

    Disposable disposable = Disposer.newDisposable();
    try {
      myFileTypeManager.getRegisteredFileTypes(); // ensure pending file types empty
      assertEmpty(myConflicts);
      createFakeType("myType", "myDisplayName", "myDescription", myWeirdExtension, disposable);
      assertNotEmpty(myConflicts);
    }
    finally {
      Disposer.dispose(disposable);
      WriteAction.run(() -> myFileTypeManager.removeAssociatedExtension(PlainTextFileType.INSTANCE, myWeirdExtension));
    }
  }

  public void testPreserveUninstalledPluginAssociations() {
    FileType typeFromPlugin = new MyTestFileType();
    FileTypeFactory factory = new FileTypeFactory() {
      @Override
      public void createFileTypes(@NotNull FileTypeConsumer consumer) {
        consumer.consume(typeFromPlugin);
      }
    };
    Element element = myFileTypeManager.getState();
    Disposable disposable = Disposer.newDisposable();
    try {
      FileTypeFactory.FILE_TYPE_FACTORY_EP.getPoint().registerExtension(factory, disposable);
      reInitFileTypeManagerComponent(element);
      WriteAction.run(() -> myFileTypeManager.associateExtension(typeFromPlugin, "foo"));
      element = myFileTypeManager.getState();
      Disposer.dispose(disposable);

      disposable = Disposer.newDisposable();
      reInitFileTypeManagerComponent(element);
      element = myFileTypeManager.getState();
      FileTypeFactory.FILE_TYPE_FACTORY_EP.getPoint().registerExtension(factory, disposable);
      reInitFileTypeManagerComponent(element);
      assertEquals(typeFromPlugin, myFileTypeManager.getFileTypeByFileName("foo.foo"));

      myFileTypeManager.unregisterFileType(typeFromPlugin);
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  // IDEA-139409 Persistent message "File type recognized: File extension *.vm was reassigned to VTL"
  public void testReassign() throws Exception {
    myFileTypeManager.getRegisteredFileTypes();
    String unresolved = "Velocity Template files";
    assertNull(myFileTypeManager.findFileTypeByName(unresolved));
    @Language("XML")
    String xml = "<component name=\"FileTypeManager\" version=\"13\">\n" +
                 "   <extensionMap>\n" +
                 "      <mapping ext=\"zip\" type=\"" + unresolved + "\" />\n" +
                 "   </extensionMap>\n" +
                 "</component>";
    Element element = JDOMUtil.load(xml);

    reInitFileTypeManagerComponent(element);
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

  public void testRemovedMappingsMustNotLeadToDuplicates() throws Exception {
    @Language("XML")
    String xml = "<blahblah version='" + FileTypeManagerImpl.VERSION + "'>\n" +
                 "   <extensionMap>\n" +
                 "   </extensionMap>\n" +
                 "</blahblah>";
    Element element = JDOMUtil.load(xml);

    myFileTypeManager.getRegisteredFileTypes(); // instantiate pending file types
    reInitFileTypeManagerComponent(element);

    List<RemovedMappingTracker.RemovedMapping> removedMappings = myFileTypeManager.getRemovedMappingTracker().getRemovedMappings();
    assertEmpty(removedMappings);

    FileType type = myFileTypeManager.getFileTypeByFileName("x.txt");
    assertEquals(PlainTextFileType.INSTANCE, type);

    RemovedMappingTracker.RemovedMapping mapping =
      myFileTypeManager.getRemovedMappingTracker().add(new ExtensionFileNameMatcher("txt"), PlainTextFileType.INSTANCE.getName(), true);

    try {
      Element result = myFileTypeManager.getState().getChildren().get(0);
      @Language("XML")
      String expectedXml = "<extensionMap>\n" +
                         "  <removed_mapping ext=\"txt\" approved=\"true\" type=\"PLAIN_TEXT\" />\n" +
                         "</extensionMap>";
      assertEquals(expectedXml, JDOMUtil.write(result));
    }
    finally {
      assertOneElement(myFileTypeManager.getRemovedMappingTracker().removeIf(m -> m.equals(mapping)));
    }
  }

  public void testDefaultFileType() {
    final String extension = "very_rare_extension";
    FileType idl = Objects.requireNonNull(myFileTypeManager.findFileTypeByName("IDL"));
    WriteAction.run(() -> myFileTypeManager.associatePattern(idl, "*." + extension));

    Element element = myFileTypeManager.getState();
    WriteAction.run(() -> myFileTypeManager.removeAssociatedExtension(idl, extension));

    reInitFileTypeManagerComponent(element);
    FileType extensions = myFileTypeManager.getFileTypeByExtension(extension);
    assertEquals("IDL", extensions.getName());
    WriteAction.run(() -> myFileTypeManager.removeAssociatedExtension(idl, extension));
  }

  public void testIfDetectorRanThenIdeaReopenedTheDetectorShouldBeReRun() throws IOException {
    UserBinaryFileType stuffType = new UserBinaryFileType() {};
    stuffType.setName("stuffType");

    Set<VirtualFile> detectorCalled = ContainerUtil.newConcurrentSet();

    FileTypeRegistry.FileTypeDetector detector = new FileTypeRegistry.FileTypeDetector() {
      @Override
      public @Nullable FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText) {
        detectorCalled.add(file);
        FileType result = FileUtil.isHashBangLine(firstCharsIfText, "stuff") ? stuffType : null;
        log("T: my detector for file "+file.getName()+" run. result="+(result == null ? null : result.getName()));
        return result;
      }

      @Override
      public int getDesiredContentPrefixLength() {
        return 48;
      }
    };
    runWithDetector(detector, () -> {
      log("T: ------ akj_dhf_ksd_jgf");
      File f = createTempFile("xx.asf_das_dfs", "akj_dhf_ksd_jgf");
      VirtualFile file = getVirtualFile(f);
      ensureReDetected(file, detectorCalled);
      assertTrue(getFileType(file).toString(), getFileType(file) instanceof PlainTextFileType);

      log("T: ------ my");
      setFileText(file,  "#!stuff\nxx");
      ensureReDetected(file, detectorCalled);
      assertEquals(stuffType, getFileType(file));

      log("T: ------ reload");
      myFileTypeManager.drainReDetectQueue();
      getPsiManager().dropPsiCaches();

      ensureReDetected(file, detectorCalled);
      assertSame(getFileType(file).toString(), getFileType(file), stuffType);
      log("T: ------");
    });
  }

  // redirect getFileType to our own test FileTypeManagerImpl instance
  private @NotNull FileType getFileType(VirtualFile file) {
    return myFileTypeManager.getFileTypeByFile(file);
  }

  public void _testStressPlainTextFileWithEverIncreasingLength() throws IOException {
    FrequentEventDetector.disableUntil(getTestRootDisposable());

    File f = createTempFile("xx.lkj_lkj_lkj_ljk", "a");
    VirtualFile virtualFile = getVirtualFile(f);
    assertEquals(PlainTextFileType.INSTANCE, getFileType(virtualFile));

    int NThreads = 8;
    int N = 1000;
    Random random = new Random();
    AtomicReference<Exception> exception = new AtomicReference<>();
    List<Thread> threads = ContainerUtil.map(new Object[NThreads], o -> new Thread(() -> {
      try {
        for (int i = 0; i < N; i++) {
          boolean isText = ReadAction.compute(() -> {
            if (getFileType(virtualFile).isBinary()) {
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

            LOG.debug(i + "; f = " + f.length() + "; virtualFile=" + virtualFile.getLength() + "; type=" + getFileType(virtualFile));
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

    File f = createTempFile("xx.asd_kjf_hlk_asj_dhf",
                            StringUtil.repeatSymbol(' ', (int)PersistentFSConstants.FILE_LENGTH_TO_CACHE_THRESHOLD - 100));
    VirtualFile virtualFile = getVirtualFile(f);
    assertEquals(PlainTextFileType.INSTANCE, getFileType(virtualFile));
    PsiFile psiFile = getPsiManager().findFile(virtualFile);
    assertTrue(psiFile instanceof PsiPlainTextFile);

    int NThreads = 1;
    int N = 10;
    List<Thread> threads = ContainerUtil.map(new Object[NThreads], o -> new Thread(() -> {
      for (int i = 0; i < N; i++) {
        ApplicationManager.getApplication().runReadAction(() -> {
          String text = psiFile.getText();
          LOG.debug("text = " + text.length());
        });
        try {
          FileUtil.appendToFile(f, StringUtil.repeatSymbol(' ', 50));
          LocalFileSystem.getInstance().refreshFiles(Collections.singletonList(virtualFile));
          LOG.debug("f = " + f.length() + "; virtualFile=" + virtualFile.getLength() +
                    "; psiFile=" + psiFile.isValid() + "; type=" + getFileType(virtualFile));
        }
        catch (IOException e) {
          throw new RuntimeException(e);
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
      VirtualFile file = createTempVirtualFile("x.sld_kfj_lsk_dfj", null, "123456789", StandardCharsets.UTF_8);
      manager.setEncoding(file, CharsetToolkit.WIN_1251_CHARSET);
      file.setCharset(CharsetToolkit.WIN_1251_CHARSET);
      UIUtil.dispatchAllInvocationEvents();
      myFileTypeManager.drainReDetectQueue();
      UIUtil.dispatchAllInvocationEvents();

      assertEquals(PlainTextFileType.INSTANCE, getFileType(file));

      manager.setEncoding(file, StandardCharsets.US_ASCII);
      UIUtil.dispatchAllInvocationEvents();
      myFileTypeManager.drainReDetectQueue();
      UIUtil.dispatchAllInvocationEvents();
      assertEquals(StandardCharsets.US_ASCII, file.getCharset());

      manager.setEncoding(file, StandardCharsets.UTF_8);
      UIUtil.dispatchAllInvocationEvents();
      myFileTypeManager.drainReDetectQueue();
      UIUtil.dispatchAllInvocationEvents();
      assertEquals(StandardCharsets.UTF_8, file.getCharset());

      manager.setEncoding(file, StandardCharsets.US_ASCII);
      UIUtil.dispatchAllInvocationEvents();
      myFileTypeManager.drainReDetectQueue();
      UIUtil.dispatchAllInvocationEvents();
      assertEquals(StandardCharsets.US_ASCII, file.getCharset());

      manager.setEncoding(file, StandardCharsets.UTF_8);
      UIUtil.dispatchAllInvocationEvents();
      myFileTypeManager.drainReDetectQueue();
      UIUtil.dispatchAllInvocationEvents();
      assertEquals(StandardCharsets.UTF_8, file.getCharset());
    }
    finally {
      manager.setDefaultCharsetName(oldProject);
    }
  }

  public void testFileTypeBeanByName() {
    assertTrue(myFileTypeManager.getStdFileType(WorkspaceFileType.INSTANCE.getName()) instanceof WorkspaceFileType);
  }

  public void testInternalFileTypeBeanIsAlwaysRegistered() {
    FileType[] types = myFileTypeManager.getRegisteredFileTypes();
    assertTrue(ContainerUtil.exists(types, type -> type instanceof WorkspaceFileType));
  }

  public void testInternalFileTypeMustBeFoundByFileName() {
    assertInstanceOf(myFileTypeManager.getFileTypeByFileName("foo" + WorkspaceFileType.DOT_DEFAULT_EXTENSION), WorkspaceFileType.class);
  }

  public void testInternalFileTypeBeanMustBeFoundByExtensionWithFieldName() {
    assertSame(ModuleFileType.INSTANCE, myFileTypeManager.getFileTypeByExtension(ModuleFileType.DEFAULT_EXTENSION));
  }

  public void testIsFileTypeMustRunDetector() throws IOException {
    VirtualFile vFile = createTempVirtualFile("x.bbb", null, "#!archive!!!", StandardCharsets.UTF_8);

    AtomicInteger detectorCalls = new AtomicInteger();
    ExtensionTestUtil.maskExtensions(FileTypeRegistry.FileTypeDetector.EP_NAME, Collections.singletonList(new FileTypeRegistry.FileTypeDetector() {
        @Override
        public @Nullable FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText) {
          if (file.equals(vFile)) {
            detectorCalls.incrementAndGet();
          }
          if (firstCharsIfText != null && firstCharsIfText.toString().startsWith("#!archive")) {
            return ArchiveFileType.INSTANCE;
          }
          return null;
        }

        @Override
        public int getDesiredContentPrefixLength() {
          return "#!archive".length();
        }
      }), getTestRootDisposable());

    assertEquals(ArchiveFileType.INSTANCE, getFileType(vFile));
    assertEquals(ArchiveFileType.INSTANCE, getFileType(vFile));
    assertEquals(1, detectorCalls.get());
  }

  public void testEveryLanguageHasOnePrimaryFileType() {
    Map<String, LanguageFileType> map = new HashMap<>();
    for (FileType type : myFileTypeManager.getRegisteredFileTypes()) {
      if (!(type instanceof LanguageFileType)) {
        continue;
      }
      LanguageFileType languageFileType = (LanguageFileType)type;
      if (!languageFileType.isSecondary()) {
        String id = languageFileType.getLanguage().getID();
        LanguageFileType oldFileType = map.get(id);
        if (oldFileType != null) {
          fail("Multiple primary file types for language " + id + ": " + oldFileType.getName() + ", " + languageFileType.getName());
        }
        map.put(id, languageFileType);
      }
    }
  }

  public void testDEFAULT_IGNOREDIsSorted() {
    List<String> strings = FileTypeManagerImpl.DEFAULT_IGNORED;
    List<String> sorted = strings.stream().sorted().collect(Collectors.toList());
    assertEquals("FileTypeManagerImpl.DEFAULT_IGNORED entries must be sorted", sorted, FileTypeManagerImpl.DEFAULT_IGNORED);
  }

  public void testRegisterUnregisterExtension() {
    FileTypeBean bean = new FileTypeBean();
    bean.name = MyTestFileType.NAME;
    bean.implementationClass = MyTestFileType.class.getName();
    Disposable disposable = registerFileType(bean, FileTypeManagerImpl.coreIdeaPluginDescriptor());

    assertNotNull(myFileTypeManager.findFileTypeByName(MyTestFileType.NAME));
    WriteAction.run(() -> Disposer.dispose(disposable));
    assertNull(myFileTypeManager.findFileTypeByName(MyTestFileType.NAME));
  }

  private static @NotNull Disposable registerFileType(@NotNull FileTypeBean bean, @NotNull PluginDescriptor pluginDescriptor) {
    bean.setPluginDescriptor(pluginDescriptor);
    Disposable disposable = Disposer.newDisposable();
    WriteAction.run(() -> FileTypeManagerImpl.EP_NAME.getPoint().registerExtension(bean, disposable));
    return disposable;
  }

  public void testRegisterUnregisterExtensionWithFileName() throws IOException {
    String name = ".veryWeirdFileName";
    File tempFile = createTempFile(name, "This is a text file");
    VirtualFile vFile = getVirtualFile(tempFile);
    assertEquals(PlainTextFileType.INSTANCE, getFileType(vFile));

    FileTypeBean bean = new FileTypeBean();
    bean.name = MyTestFileType.NAME;
    bean.fileNames = name;
    bean.implementationClass = MyTestFileType.class.getName();
    Disposable disposable = registerFileType(bean, FileTypeManagerImpl.coreIdeaPluginDescriptor());
    try {
      clearFileTypeCache();

      assertEquals(MyTestFileType.NAME, myFileTypeManager.getFileTypeByFileName(name).getName());
      assertEquals(MyTestFileType.NAME, getFileType(vFile).getName());
    }
    finally {
      WriteAction.run(() -> Disposer.dispose(disposable));
    }

    assertNull(myFileTypeManager.findFileTypeByName(MyTestFileType.NAME));
  }

  private static void clearFileTypeCache() {
    WriteAction.run(() -> CachedFileType.clearCache());   // normally this is done by PsiModificationTracker.Listener but it's not fired in this test
  }

  public void testRegisterAdditionalExtensionForExistingFileType() throws IOException {
    String name = ".veryUnknownFile";
    File tempFile = createTempFile(name, "This is a text file");
    VirtualFile vFile = getVirtualFile(tempFile);
    assertEquals(PlainTextFileType.INSTANCE, getFileType(vFile));

    FileTypeBean bean = new FileTypeBean();
    bean.name = "XML";
    bean.fileNames = name;
    Disposable disposable = registerFileType(bean, FileTypeManagerImpl.coreIdeaPluginDescriptor());
    try {
      clearFileTypeCache();

      assertEquals("XML", myFileTypeManager.getFileTypeByFileName(name).getName());
      assertEquals("XML", getFileType(vFile).getName());
    }
    finally {
      WriteAction.run(() -> Disposer.dispose(disposable));
    }

    assertEquals("UNKNOWN", myFileTypeManager.getFileTypeByFileName(name).getName());
  }

  public void testRegisterAssociationsViaFileTypeFactoryDoesWork() {
    FileType anyExistingType = StdFileTypes.XML;
    FileType ownType = new FileType() {
      @Override
      public @NotNull String getName() {
        return getTestName(false) + "_FileType";
      }

      @Override
      public @NotNull String getDescription() {
        return getTestName(false) + "_Description";
      }

      @Override
      public @NotNull String getDefaultExtension() {
        return getTestName(false) + "_Extension";
      }

      @Override
      public @Nullable Icon getIcon() {
        return null;
      }

      @Override
      public boolean isBinary() {
        return false;
      }
    };

    // I suspect it may work when registered once, need more than 1
    String[] extensionsForXml = {getTestName(true)+"_1", getTestName(true)+"_2", getTestName(true)+"_3" };

    for (String nextExtension : ContainerUtil.concat(extensionsForXml, new String[]{ownType.getDefaultExtension()})) {
      assertEquals("precondition: should be unknown before: " + nextExtension,
                   FileTypes.UNKNOWN, myFileTypeManager.getFileTypeByExtension(nextExtension));
    }

    Ref<Boolean> factoryWasCalled = new Ref<>(false);

    FileTypeFactory factory = new FileTypeFactory() {
      @Override
      public void createFileTypes(@NotNull FileTypeConsumer consumer) {
        factoryWasCalled.set(true);

        consumer.consume(ownType);

        for (String nextExtension : extensionsForXml) {
          consumer.consume(anyExistingType, nextExtension);
        }
      }
    };

    Disposable disposable = Disposer.newDisposable();
    try {
      clearFileTypeCache();
      FileTypeFactory.FILE_TYPE_FACTORY_EP.getPoint().registerExtension(factory, disposable);

      assertFalse(factoryWasCalled.get());
      reInitFileTypeManagerComponent(null);
      assertTrue(factoryWasCalled.get());

      assertEquals("factory was called, new types work fine",
                   ownType, myFileTypeManager.getFileTypeByExtension(ownType.getDefaultExtension()));

      // it works for own types but does not for the external types
      for (String nextExtension : extensionsForXml) {
        assertEquals("factory was called but extension is still unknown : " + nextExtension,
                     anyExistingType, myFileTypeManager.getFileTypeByExtension(nextExtension));
      }
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  public void testPluginOverridesAbstractFileType() {
    assertInstanceOf(myFileTypeManager.findFileTypeByName(MyHaskellFileType.NAME), AbstractFileType.class);

    FileTypeBean bean = new FileTypeBean();
    bean.name = MyHaskellFileType.NAME;
    bean.extensions = "hs";
    bean.implementationClass = MyHaskellFileType.class.getName();
    Disposable disposable = registerFileType(bean, FileTypeManagerImpl.coreIdeaPluginDescriptor());

    assertInstanceOf(myFileTypeManager.findFileTypeByName(MyHaskellFileType.NAME), MyHaskellFileType.class);
    assertInstanceOf(myFileTypeManager.getFileTypeByFileName("foo.hs"), MyHaskellFileType.class);

    WriteAction.run(() -> Disposer.dispose(disposable));

    // todo restore old AbstractFileType automatically?
    AbstractFileType old = new AbstractFileType(new SyntaxTable());
    old.setName(MyHaskellFileType.NAME);
    myFileTypeManager.registerFileType(old, List.of(), myFileTypeManager);
  }

  private static class MyCustomImageFileType implements FileType {
    private MyCustomImageFileType() { }
    @Override public @NotNull String getName() { return "my.image"; }
    @Override public @NotNull String getDisplayName() { return getClass().getName(); }
    @Override public @NotNull String getDescription() { return getDisplayName(); }
    @Override public @NotNull String getDefaultExtension() { return "hs"; }
    @Override public @Nullable Icon getIcon() { return null; }
    @Override  public boolean isBinary() { return false; }
  }

  private static class MyCustomImageFileType2 extends MyCustomImageFileType {
    @Override public @NotNull String getName() { return super.getName() + "2"; }
  }

  public void testPluginWhichOverridesBundledFileTypeMustWin() {
    FileType bundled = Objects.requireNonNull(myFileTypeManager.findFileTypeByName("Image"));
    // this check doesn't work because in unit test mode plugin class loader is not created
    //PluginDescriptor pluginDescriptor = PluginManager.getPluginByClass(bundled.getClass());
    //assertTrue(pluginDescriptor.isBundled());
    //LOG.debug("pluginDescriptor = " + pluginDescriptor);

    FileTypeBean bean = new FileTypeBean();
    bean.name = new MyCustomImageFileType().getName();
    String ext = myFileTypeManager.getAssociations(bundled).get(0).toString().replace("*.", "");
    bean.extensions = ext;
    bean.implementationClass = MyCustomImageFileType.class.getName();
    bean.setPluginDescriptor(new DefaultPluginDescriptor(PluginId.getId("myTestPlugin"),
                                                         PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID).getPluginClassLoader()));
    Disposable disposable = Disposer.newDisposable();
    Disposer.register(getTestRootDisposable(), disposable);
    WriteAction.run(() -> FileTypeManagerImpl.EP_NAME.getPoint().registerExtension(bean, disposable));

    assertInstanceOf(myFileTypeManager.findFileTypeByName(bean.name), MyCustomImageFileType.class);
    assertInstanceOf(myFileTypeManager.getFileTypeByExtension(ext), MyCustomImageFileType.class);

    WriteAction.run(() -> Disposer.dispose(disposable));
  }

  private void assertFileTypeIsUnregistered(@NotNull FileType fileType) {
    for (FileType type : myFileTypeManager.getRegisteredFileTypes()) {
      if (type.getClass() == fileType.getClass()) {
        throw new AssertionError(type + " is still registered");
      }
    }
    FileType registered = myFileTypeManager.findFileTypeByName(fileType.getName());
    if (registered != null && !(registered instanceof AbstractFileType)) {
      fail(registered.toString());
    }
  }

  public void testTwoPluginsWhichOverrideBundledFileTypeMustNegotiateBetweenThemselves() {
    FileTypeManager fileTypeManager = myFileTypeManager;
    FileType bundled = Objects.requireNonNull(fileTypeManager.findFileTypeByName("Image"));
    //PluginDescriptor pluginDescriptor = Objects.requireNonNull(PluginManagerCore.getPluginDescriptorOrPlatformByClassName(bundled.getClass().getName()));
    //assertTrue(pluginDescriptor.isBundled());
    //LOG.debug("pluginDescriptor = " + pluginDescriptor);

    String ext = myFileTypeManager.getAssociations(bundled).get(0).toString().replace("*.", "");
    FileTypeBean bean = new FileTypeBean();
    bean.name = new MyCustomImageFileType().getName();
    bean.extensions = ext;
    bean.implementationClass = MyCustomImageFileType.class.getName();
    bean.setPluginDescriptor(new DefaultPluginDescriptor(PluginId.getId("myTestPlugin"),
                                                         FileTypeManagerImpl.coreIdeaPluginDescriptor().getPluginClassLoader()));

    FileTypeBean bean2 = new FileTypeBean();
    bean2.name = new MyCustomImageFileType2().getName();
    bean2.extensions = ext;
    bean2.implementationClass = MyCustomImageFileType2.class.getName();
    bean2.setPluginDescriptor(new DefaultPluginDescriptor(PluginId.getId("myTestPlugin2"),
                                                          FileTypeManagerImpl.coreIdeaPluginDescriptor().getPluginClassLoader()));

    Disposable disposable = Disposer.newDisposable();
    Disposer.register(getTestRootDisposable(), disposable);

    WriteAction.run(() -> {
      FileTypeManagerImpl.EP_NAME.getPoint().registerExtension(bean, disposable);
      FileTypeManagerImpl.EP_NAME.getPoint().registerExtension(bean2, disposable);
    });

    assertInstanceOf(fileTypeManager.findFileTypeByName(bean.name), MyCustomImageFileType.class);
    assertInstanceOf(fileTypeManager.findFileTypeByName(bean2.name), MyCustomImageFileType2.class);
    assertInstanceOf(fileTypeManager.getFileTypeByExtension(ext), MyCustomImageFileType.class); // either MyCustomImageFileType or MyCustomImageFileType2

    WriteAction.run(() -> Disposer.dispose(disposable));
  }

  public void testHashBangPatternsCanBeConfiguredDynamically() throws IOException {
    VirtualFile file0 = createTempVirtualFile("x.xxx", null, "#!/usr/bin/go-go-go\na=b", StandardCharsets.UTF_8);
    assertEquals(PlainTextFileType.INSTANCE, getFileType(file0));
    FileType PROPERTIES = FileTypeManager.getInstance().getStdFileType("Properties");
    FileTypeManagerImpl.FileTypeWithDescriptor fileType = FileTypeManagerImpl.coreDescriptorFor(PROPERTIES);
    myFileTypeManager.getExtensionMap().addHashBangPattern("go-go-go", fileType);
    try {
      VirtualFile file = createTempVirtualFile("x.xxx", null, "#!/usr/bin/go-go-go\na=b", StandardCharsets.UTF_8);
      assertEquals(PROPERTIES, getFileType(file));
    }
    finally {
      myFileTypeManager.getExtensionMap().removeHashBangPattern("go-go-go", fileType);
    }
  }

  private static class MyTestFileType implements FileType {
    public static final String NAME = "Foo files";
    public static final String EXTENSION = "from_test_plugin";

    private MyTestFileType() { }

    @Override public @NotNull String getName() { return NAME; }
    @Override public @NotNull String getDescription() { return ""; }
    @Override public @NotNull String getDefaultExtension() { return EXTENSION; }
    @Override public @Nullable Icon getIcon() { return null; }
    @Override public boolean isBinary() { return false; }
  }

  private static class MyHaskellFileType implements FileType {
    public static final String NAME = "Haskell";

    private MyHaskellFileType() { }

    @Override public @NotNull String getName() { return NAME; }
    @Override public @NotNull String getDescription() { return ""; }
    @Override public @NotNull String getDefaultExtension() { return "hs"; }
    @Override public @Nullable Icon getIcon() { return null; }
    @Override public boolean isBinary() { return false; }
  }

  public void testFileTypeConstructorsMustBeNonPublic() {
    FileType[] fileTypes = myFileTypeManager.getRegisteredFileTypes();
    LOG.debug("Registered file types: "+fileTypes.length);
    for (FileType fileType : fileTypes) {
      assertEquals(fileType, fileType); // assert reflexivity
      if (fileType.getClass() == AbstractFileType.class) {
        continue;
      }
      Constructor<?>[] constructors = fileType.getClass().getDeclaredConstructors();
      for (Constructor<?> constructor : constructors) {
        assertFalse("FileType constructor must be non-public to avoid duplicates but got: " + constructor, Modifier.isPublic(constructor.getModifiers()));
      }
    }
  }

  public void testDetectedAsTextMustNotStuckWithUnknownFileTypeWhenShrunkToZeroLength() throws IOException {
    File f = createTempFile("xx.lkj_lkj_lkj_ljk", "a");
    VirtualFile virtualFile = getVirtualFile(f);
    assertEquals(PlainTextFileType.INSTANCE, getFileType(virtualFile));

    setBinaryContent(virtualFile, new byte[0]);
    myFileTypeManager.drainReDetectQueue();
    FileType type = getFileType(virtualFile);
    assertFalse(type.toString(), type.isBinary()); // either PlainTextFile or DetectedByContent are fine

    setBinaryContent(virtualFile, "qwe\newq".getBytes(StandardCharsets.UTF_8));
    myFileTypeManager.drainReDetectQueue();
    assertEquals(PlainTextFileType.INSTANCE, getFileType(virtualFile));
  }

  public void testRegisterFileTypesWithIdenticalDisplayNameOrDescriptionMustThrow() {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());

    Disposable disposable1 = Disposer.newDisposable();
    try {
      createFakeType("myCreativeName0", "display1", "descr1", "ext1", disposable1);
      assertThrows(Throwable.class, () -> createFakeType("myCreativeName1", "display1", "descr2", "ext2", disposable1));
    }
    finally {
      Disposer.dispose(disposable1);
    }

    Disposable disposable2 = Disposer.newDisposable();
    try {
      createFakeType("myCreativeName2", "display0", "descr", "ext1", disposable2);
      assertThrows(Throwable.class, () -> createFakeType("myCreativeName3", "display1", "descr", "ext2", disposable2));
    }
    finally {
      Disposer.dispose(disposable2);
    }
  }

  private @NotNull FileType createFakeType(@NotNull String name,
                                           @NotNull String displayName,
                                           @NotNull String description,
                                           @NotNull String extension,
                                           @NotNull Disposable disposable) {
    FileType myType = new FakeFileType() {
      @Override public boolean isMyFileType(@NotNull VirtualFile file) { return false; }
      @Override public @NotNull String getName() { return name; }
      @Override public @Nls @NotNull String getDisplayName() { return displayName; }
      @Override public @NotNull @NlsContexts.Label String getDescription() { return description; }
    };
    myFileTypeManager.registerFileType(myType, List.of(new ExtensionFileNameMatcher(extension)), disposable);
    return myType;
  }

  public void testDetectorMustWorkForEmptyFileNow() throws IOException {
    Set<VirtualFile> detectorCalled = ContainerUtil.newConcurrentSet();
    String magicName = "blah-blah.to.detect";
    FileTypeRegistry.FileTypeDetector detector = (file, __, ___) -> {
      detectorCalled.add(file);
      return file.getName().equals(magicName) ? new MyTestFileType() : null;
    };
    runWithDetector(detector, () -> {
      VirtualFile vFile = createTempVirtualFile(magicName, null, "", StandardCharsets.UTF_8);
      ensureReDetected(vFile, detectorCalled);
      assertTrue(getFileType(vFile).toString(), getFileType(vFile) instanceof MyTestFileType);
    });
  }

  public void testNewRegisteredFileTypeWithMatchersDuplicatingNativeFileTypeMustWin() {
    String nativeExt = myFileTypeManager.getAssociations(NativeFileType.INSTANCE).get(0).toString().replace("*.", "");
    assertFalse(StringUtil.isEmpty(nativeExt));
    assertEquals(NativeFileType.INSTANCE, myFileTypeManager.getFileTypeByExtension(nativeExt));
    FakeFileType newFileType = new FakeFileType() {
      @Override public boolean isMyFileType(@NotNull VirtualFile file) { return false; }
      @Override public @NotNull String getName() { return "Foo"; }
      @Override public @NotNull String getDescription() { return "Foo"; }
    };
    Disposable disposable = Disposer.newDisposable();
    try {
      myFileTypeManager.registerFileType(newFileType, List.of(new ExtensionFileNameMatcher(nativeExt)), disposable);
      assertEquals("Foo", myFileTypeManager.getFileTypeByFileName("foo." + nativeExt).getName());
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  public void testFileTypeManagerInitMustReadRemovedMappingBeforeShowingConflictNotification() {
    String myWeirdExtension = "my_weird_extension";
    myFileTypeManager.getRemovedMappingTracker().add(new ExtensionFileNameMatcher(myWeirdExtension), PlainTextFileType.INSTANCE.getName(), true);
    Element stateWithRemovedMapping = myFileTypeManager.getState();

    WriteAction.run(() -> myFileTypeManager.associateExtension(PlainTextFileType.INSTANCE, myWeirdExtension));
    Disposable disposable = Disposer.newDisposable();
    try {
      // now, ordinarily we'd get conflict, but thanks to externalized removed mapping tracker, we won't
      reInitFileTypeManagerComponent(stateWithRemovedMapping);
      FileType myType = createFakeType("myType", "myDisplayName", "myDescription", myWeirdExtension, disposable);
      assertEmpty(myConflicts);
      assertEquals(myType, myFileTypeManager.getFileTypeByExtension(myWeirdExtension));
    }
    finally {
      Disposer.dispose(disposable);
      WriteAction.run(() -> myFileTypeManager.removeAssociatedExtension(PlainTextFileType.INSTANCE, myWeirdExtension));
    }
  }

  public void testFileTypeManagerInitMustReadRemovedMappingBeforeShowingConflictNotification2() {
    String myWeirdExtension = "my_weird_extension";
    Disposable disposable = Disposer.newDisposable();
    try {
      FileType myType = createFakeType("myType", "myDisplayName", "myDescription", myWeirdExtension, disposable);

      myFileTypeManager.getRemovedMappingTracker().add(new ExtensionFileNameMatcher(myWeirdExtension), myType.getName(), true);
      Element stateWithRemovedMapping = myFileTypeManager.getState();

      WriteAction.run(() -> myFileTypeManager.associateExtension(PlainTextFileType.INSTANCE, myWeirdExtension));
      // now, ordinarily we'd get conflict, but thanks to externalized removed mapping tracker, we won't
      reInitFileTypeManagerComponent(stateWithRemovedMapping);
      assertEmpty(myConflicts);
      assertEquals(PlainTextFileType.INSTANCE, myFileTypeManager.getFileTypeByExtension(myWeirdExtension));
    }
    finally {
      Disposer.dispose(disposable);
      WriteAction.run(() -> myFileTypeManager.removeAssociatedExtension(PlainTextFileType.INSTANCE, myWeirdExtension));
    }
  }

  public void testIsFileOfTypeMustNotQueryAllFileTypesIdentifiableByVirtualFileForPerformanceReasons() throws IOException {
    Disposable disposable = Disposer.newDisposable();
    try {
      AtomicInteger myFileTypeCalledCount = new AtomicInteger();
      class MyFileTypeIdentifiableByFile extends FakeFileType {
        @Override public boolean isMyFileType(@NotNull VirtualFile file) { myFileTypeCalledCount.incrementAndGet(); return false; }
        @Override public @NotNull String getName() { return "myfake"; }
        @Override public @Nls @NotNull String getDisplayName() { return getName(); }
        @Override public @NotNull @NlsContexts.Label String getDescription() { return getName(); }
      }
      myFileTypeManager.registerFileType(new MyFileTypeIdentifiableByFile(), List.of(), disposable);

      AtomicInteger otherFileTypeCalledCount = new AtomicInteger();
      class MyOtherFileTypeIdentifiableByFile extends FakeFileType {
        @Override public boolean isMyFileType(@NotNull VirtualFile file) {
          otherFileTypeCalledCount.incrementAndGet();
          return false;
        }
        @Override public @NotNull String getName() { return "myotherfake"; }
        @Override public @Nls @NotNull String getDisplayName() { return getName(); }
        @Override public @NotNull @NlsContexts.Label String getDescription() { return getName(); }
      }
      myFileTypeManager.registerFileType(new MyOtherFileTypeIdentifiableByFile(), List.of(), disposable);

      File f = createTempFile("xx.lkj_lkj_lkj_ljk", "a");
      VirtualFile virtualFile = getVirtualFile(f);

      FakeVirtualFile vf = new FakeVirtualFile(virtualFile, "myname.myname") {
        @Override
        public @NotNull FileType getFileType() {
          return myFileTypeManager.getFileTypeByFile(this); // otherwise this call will be redirected to FileTypeManger.getInstance() which is not what we are testing
        }

        @Override
        public boolean isValid() {
          return false; //to avoid detect by content
        }
      };
      FileType ft = myFileTypeManager.getFileTypeByFile(vf);
      assertEquals(UnknownFileType.INSTANCE, ft);
      // during getFileType() we must check all possible file types
      assertTrue(myFileTypeCalledCount.toString(), myFileTypeCalledCount.get() > 0);
      assertTrue(otherFileTypeCalledCount.toString(), otherFileTypeCalledCount.get() > 0);

      myFileTypeCalledCount.set(0);
      otherFileTypeCalledCount.set(0);
      assertFalse(myFileTypeManager.isFileOfType(vf, PlainTextFileType.INSTANCE));
      assertEquals(myFileTypeCalledCount.toString(), 0, myFileTypeCalledCount.get());
      assertEquals(otherFileTypeCalledCount.toString(), 0, otherFileTypeCalledCount.get());

      assertFalse(myFileTypeManager.isFileOfType(vf, new MyOtherFileTypeIdentifiableByFile()));
      assertEquals(myFileTypeCalledCount.toString(), 0, myFileTypeCalledCount.get()); // must not call irrelevant file types
      assertTrue(otherFileTypeCalledCount.toString(), otherFileTypeCalledCount.get() > 0); // must call requested file type
    }
    finally {
      Disposer.dispose(disposable);
    }
  }
}
