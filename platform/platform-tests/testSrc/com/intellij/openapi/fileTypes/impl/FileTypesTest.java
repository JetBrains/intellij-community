// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
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
import com.intellij.openapi.vfs.newvfs.impl.CachedFileType;
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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
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
  private FileTypeManagerImpl myFileTypeManager;
  private String myOldIgnoredFilesList;
  private @NotNull List<FileTypeManagerImpl.FileTypeWithDescriptor>
    myOldRegisteredTypes;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFileTypeManager = (FileTypeManagerImpl)FileTypeManagerEx.getInstanceEx();
    myOldIgnoredFilesList = myFileTypeManager.getIgnoredFilesList();
    FileTypeManagerImpl.reDetectAsync(true);
    Assume.assumeTrue(
      "Test must be run under community classpath because otherwise everything would break thanks to weird HelmYamlLanguage which is created on each HelmYamlFileType registration which happens a lot in these tests",
      PlatformTestUtil.isUnderCommunityClassPath());
    myOldRegisteredTypes = new ArrayList<>(myFileTypeManager.getRegisteredFileTypeWithDescriptors());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      assertFileTypeIsUnregistered(new MyCustomImageFileType());
      assertFileTypeIsUnregistered(new MyCustomImageFileType2());
      assertFileTypeIsUnregistered(new MyTestFileType());
      assertFileTypeIsUnregistered(new MyHaskellFileType());
      assertEmpty(myFileTypeManager.getRemovedMappingTracker().getRemovedMappings());
      List<FileTypeManagerImpl.FileTypeWithDescriptor> newRegisteredTypes = new ArrayList<>(myFileTypeManager.getRegisteredFileTypeWithDescriptors());
      myOldRegisteredTypes.sort(Comparator.comparing(FileTypeManagerImpl.FileTypeWithDescriptor::getName));
      newRegisteredTypes.sort(Comparator.comparing(FileTypeManagerImpl.FileTypeWithDescriptor::getName));
      assertSameElements(ContainerUtil.map(newRegisteredTypes, f->f.getName()), ContainerUtil.map(myOldRegisteredTypes, f->f.getName()));
      FileTypeManagerImpl.reDetectAsync(false);
      WriteAction.run(() -> myFileTypeManager.setIgnoredFilesList(myOldIgnoredFilesList));
      initStandardFileTypes();
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

  private @NotNull List<ConflictingFileTypeMappingTracker.ResolveConflictResult> initStandardFileTypes() {
    myFileTypeManager.getRegisteredFileTypes(); // ensure pending file types empty
    return myFileTypeManager.initStandardFileTypes();
  }

  public void testMaskExclude() {
    final String pattern1 = "a*b.c?d";
    final String pattern2 = "xxx";
    WriteAction.run(() -> myFileTypeManager.setIgnoredFilesList(pattern1 + ";" + pattern2));
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
    WriteAction.run(() -> myFileTypeManager.setIgnoredFilesList("1*2;3*4;5*6;7*8;9*0;*1;*3;*5;*6;7*;*8*"));
    String[] names = new String[100];
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
    FileTypeManagerEx manager = FileTypeManagerEx.getInstanceEx();
    WriteAction.run(() -> manager.setIgnoredFilesList("a;b;"));
    assertEquals("a;b;", manager.getIgnoredFilesList());
    WriteAction.run(() -> manager.setIgnoredFilesList("b;a;"));
    assertEquals("b;a;", manager.getIgnoredFilesList());
  }

  public void testIgnoredFiles() throws IOException {
    VirtualFile file = getVirtualFile(createTempFile(".svn", ""));
    assertTrue(myFileTypeManager.isFileIgnored(file));
    assertFalse(myFileTypeManager.isFileIgnored(createTempVirtualFile("x.txt", null, "", StandardCharsets.UTF_8)));
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
    assertEquals(FileTypes.PLAIN_TEXT, virtualFile.getFileType());
  }

  public void testAutoDetectedWhenDocumentWasCreated() throws IOException {
    File dir = createTempDirectory();
    File file = FileUtil.createTempFile(dir, "x", "xxx_xx_xx", true);
    FileUtil.writeToFile(file, "xxx xxx xxx xxx");
    VirtualFile virtualFile = getVirtualFile(file);
    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    assertNotNull(document);
    assertEquals(FileTypes.PLAIN_TEXT, virtualFile.getFileType());
  }

  public void testAutoDetectionShouldNotBeOverEager() throws IOException {
    File dir = createTempDirectory();
    File file = FileUtil.createTempFile(dir, "x", "xxx_xx_xx", true);
    FileUtil.writeToFile(file, "xxx xxx xxx xxx");
    VirtualFile virtualFile = getVirtualFile(file);
    FileType fileType = virtualFile.getFileType();

    assertEquals(myFileTypeManager.getRemovedMappingTracker().getRemovedMappings().toString(), PlainTextFileType.INSTANCE, fileType);
  }

  public void testAutoDetectEmptyFile() throws IOException {
    File dir = createTempDirectory();
    File file = FileUtil.createTempFile(dir, "x", "xxx_xx_xx", true);
    VirtualFile virtualFile = getVirtualFile(file);
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
    assertEquals(CharsetToolkit.GuessedEncoding.BINARY,
                 new CharsetToolkit(bytes, Charset.defaultCharset(), false).guessFromContent(bytes.length));
    File f = new File(d, "xx.asfdasdfas");
    FileUtil.writeToFile(f, bytes);

    VirtualFile vFile = getVirtualFile(f);

    assertEquals(UnknownFileType.INSTANCE, vFile.getFileType());
  }

  public void test7BitIsText() throws IOException {
    File d = createTempDirectory();
    byte[] bytes = {9, 10, 13, 'x', 'a', 'b'};
    assertEquals(CharsetToolkit.GuessedEncoding.SEVEN_BIT,
                 new CharsetToolkit(bytes, Charset.defaultCharset(), false).guessFromContent(bytes.length));
    File f = new File(d, "xx.asfdasdfas");
    FileUtil.writeToFile(f, bytes);
    VirtualFile vFile = getVirtualFile(f);

    assertEquals(PlainTextFileType.INSTANCE, vFile.getFileType());
  }

  public void testReDetectOnContentsChange() throws IOException {
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
      @Nullable
      @Override
      public FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText) {
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
    });
  }

  private <T extends Throwable> void runWithDetector(@NotNull FileTypeRegistry.@NotNull FileTypeDetector detector, @NotNull ThrowableRunnable<T> runnable) throws T {
    FileTypeRegistry.FileTypeDetector.EP_NAME.getPoint().registerExtension(detector, getTestRootDisposable());
    FileTypeManagerImpl fileTypeManager = (FileTypeManagerImpl)FileTypeManager.getInstance();
    fileTypeManager.toLog = true;
    try {
      runnable.run();
    }
    finally {
      fileTypeManager.toLog = false;
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

  public void testRemovedMappingsSerialization() {
    Set<FileTypeManagerImpl.FileTypeWithDescriptor> fileTypes = new HashSet<>(myFileTypeManager.getRegisteredFileTypeWithDescriptors());
    FileTypeAssocTable<FileTypeManagerImpl.FileTypeWithDescriptor> table = myFileTypeManager.getExtensionMap().copy();

    FileTypeManagerImpl.FileTypeWithDescriptor fileType = FileTypeManagerImpl.coreDescriptorFor(ArchiveFileType.INSTANCE);
    FileNameMatcher matcher = table.getAssociations(fileType).get(0);

    table.removeAssociation(matcher, fileType);

    WriteAction.run(() -> myFileTypeManager.setPatternsTable(fileTypes, table));
    myFileTypeManager.getRemovedMappingTracker().add(matcher, fileType.fileType.getName(), true);

    Element state = myFileTypeManager.getState();

    myFileTypeManager.getRemovedMappingTracker().clear();
    initStandardFileTypes();
    myFileTypeManager.loadState(state);
    myFileTypeManager.initializeComponent();

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

    myFileTypeManager.getRemovedMappingTracker().clear();
    initStandardFileTypes();
    myFileTypeManager.loadState(state);
    myFileTypeManager.initializeComponent();

    List<RemovedMappingTracker.RemovedMapping> mappings = myFileTypeManager.getRemovedMappingTracker().getRemovedMappings();
    assertTrue(mappings.get(0).isApproved());
    assertEquals(matcher, mappings.get(0).getFileNameMatcher());
    assertOneElement(myFileTypeManager.getRemovedMappingTracker().removeIf(mapping -> mapping.getFileNameMatcher().equals(matcher)));
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

    WriteAction.run(() -> myFileTypeManager
      .setPatternsTable(new HashSet<>(myFileTypeManager.getRegisteredFileTypeWithDescriptors()),
                        myFileTypeManager.getExtensionMap().copy()));
    assertEmpty(myFileTypeManager.getRemovedMappingTracker().getRemovedMappings());
  }

  public void testPreserveRemovedMappingForUnknownFileType() {
    myFileTypeManager.getRemovedMappingTracker().add(new ExtensionFileNameMatcher("xxx"), MyTestFileType.NAME, true);
    WriteAction.run(() -> myFileTypeManager
      .setPatternsTable(new HashSet<>(myFileTypeManager.getRegisteredFileTypeWithDescriptors()),
                        myFileTypeManager.getExtensionMap().copy()));
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

    rename(vFile, "xx.zxmcnbzmxnbc");
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

  // for IDEA-114804 File types mapped to text are not remapped when corresponding plugin is installed
  public void testRemappingToInstalledPluginExtension() throws WriteExternalException, InvalidDataException {
    myFileTypeManager.getRegisteredFileTypes();
    WriteAction.run(() -> myFileTypeManager.associatePattern(PlainTextFileType.INSTANCE, "*." + MyTestFileType.EXTENSION));

    Element element = myFileTypeManager.getState();

    FileTypeBean bean = new FileTypeBean();
    bean.name = MyTestFileType.NAME;
    bean.implementationClass = MyTestFileType.class.getName();
    bean.extensions = MyTestFileType.EXTENSION;
    IdeaPluginDescriptorImpl pluginDescriptor = PluginDescriptorTestKt
      .readDescriptorForTest(Path.of(""), false, "<idea-plugin/>".getBytes(StandardCharsets.UTF_8), PluginId.getId("myPlugin"));
    Disposable disposable = registerFileType(bean, pluginDescriptor);
    myFileTypeManager.getRegisteredFileTypes();
    initStandardFileTypes();
    myFileTypeManager.loadState(element);

    myFileTypeManager.initializeComponent();
    List<RemovedMappingTracker.RemovedMapping> mappings = myFileTypeManager.getRemovedMappingTracker().getRemovedMappings();
    assertEquals(1, mappings.size());
    assertEquals(MyTestFileType.NAME, mappings.get(0).getFileTypeName());
    WriteAction.run(() -> Disposer.dispose(disposable));
  }

  public void testRegisterConflictingExtensionMustBeReported() throws WriteExternalException, InvalidDataException {
    String myWeirdExtension = "fromPlugin";
    WriteAction.run(() -> myFileTypeManager.associateExtension(PlainTextFileType.INSTANCE, myWeirdExtension));

    Disposable disposable = Disposer.newDisposable();
    try {
      FileType myType = createFakeType("myType", "myDispl", "mydescr", myWeirdExtension, disposable);
      myFileTypeManager.getRegisteredFileTypes();
      assertNotEmpty(initStandardFileTypes());
      myFileTypeManager.unregisterFileType(myType);
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
      initStandardFileTypes();
      myFileTypeManager.loadState(element);
      myFileTypeManager.initializeComponent();

      WriteAction.run(() -> myFileTypeManager.associatePattern(typeFromPlugin, "*.foo"));

      element = myFileTypeManager.getState();

      Disposer.dispose(disposable);
      disposable = Disposer.newDisposable();
      initStandardFileTypes();
      myFileTypeManager.loadState(element);
      myFileTypeManager.initializeComponent();

      element = myFileTypeManager.getState();

      FileTypeFactory.FILE_TYPE_FACTORY_EP.getPoint().registerExtension(factory, disposable);
      initStandardFileTypes();
      myFileTypeManager.loadState(element);
      myFileTypeManager.initializeComponent();

      assertEquals(typeFromPlugin, myFileTypeManager.getFileTypeByFileName("foo.foo"));

      myFileTypeManager.unregisterFileType(typeFromPlugin);
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  // IDEA-139409 Persistent message "File type recognized: File extension *.vm was reassigned to VTL"
  public void testReassign() throws Exception {
    initStandardFileTypes();
    myFileTypeManager.initializeComponent();

    @Language("XML")
    String xml = "<component name=\"FileTypeManager\" version=\"13\">\n" +
                 "   <extensionMap>\n" +
                 "      <mapping ext=\"zip\" type=\"Velocity Template files\" />\n" +
                 "   </extensionMap>\n" +
                 "</component>";
    Element element = JDOMUtil.load(xml);

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

  public void testRemovedMappingsMustNotLeadToDuplicates() throws Exception {
    @Language("XML")
    String xml = "<blahblah>\n" +
                 "   <extensionMap>\n" +
                 "   </extensionMap>\n" +
                 "</blahblah>";
    Element element = JDOMUtil.load(xml);

    myFileTypeManager.loadState(element);
    myFileTypeManager.initializeComponent();

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
    final String extension = "veryRareExtension";
    FileType idl = Objects.requireNonNull(myFileTypeManager.findFileTypeByName("IDL"));
    WriteAction.run(() -> myFileTypeManager.associatePattern(idl, "*." + extension));

    Element element = myFileTypeManager.getState();
    //log(JDOMUtil.writeElement(element));
    WriteAction.run(() -> myFileTypeManager.removeAssociatedExtension(idl, extension));

    initStandardFileTypes();
    myFileTypeManager.loadState(element);
    myFileTypeManager.initializeComponent();
    FileType extensions = myFileTypeManager.getFileTypeByExtension(extension);
    assertEquals("IDL", extensions.getName());
    WriteAction.run(() -> myFileTypeManager.removeAssociatedExtension(idl, extension));
  }

  public void testIfDetectorRanThenIdeaReopenedTheDetectorShouldBeReRun() throws IOException {
    UserBinaryFileType stuffType = new UserBinaryFileType() {};
    stuffType.setName("stuffType");

    Set<VirtualFile> detectorCalled = ContainerUtil.newConcurrentSet();

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
      public int getDesiredContentPrefixLength() {
        return 48;
      }
    };
    runWithDetector(detector, () -> {
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
      getPsiManager().dropPsiCaches();

      ensureRedetected(file, detectorCalled);
      assertSame(file.getFileType().toString(), file.getFileType(), stuffType);
      log("T: ------");
    });
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

    File f = createTempFile("xx.asdkjfhlkasjdhf",
                            StringUtil.repeatSymbol(' ', (int)PersistentFSConstants.FILE_LENGTH_TO_CACHE_THRESHOLD - 100));
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
        try {
          FileUtil.appendToFile(f, StringUtil.repeatSymbol(' ', 50));
          LocalFileSystem.getInstance().refreshFiles(Collections.singletonList(virtualFile));
          LOG.debug("f = " + f.length() + "; virtualFile=" + virtualFile.getLength() +
                    "; psiFile=" + psiFile.isValid() + "; type=" + virtualFile.getFileType());
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
      VirtualFile file = createTempVirtualFile("x.sldkfjlskdfj", null, "123456789", StandardCharsets.UTF_8);
      manager.setEncoding(file, CharsetToolkit.WIN_1251_CHARSET);
      file.setCharset(CharsetToolkit.WIN_1251_CHARSET);
      UIUtil.dispatchAllInvocationEvents();
      myFileTypeManager.drainReDetectQueue();
      UIUtil.dispatchAllInvocationEvents();

      assertEquals(PlainTextFileType.INSTANCE, file.getFileType());

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
    assertTrue(myFileTypeManager.getStdFileType("IDEA_WORKSPACE") instanceof WorkspaceFileType);
  }

  public void testFileTypeBeanRegistered() {
    FileType[] types = myFileTypeManager.getRegisteredFileTypes();
    assertTrue(ContainerUtil.exists(types, type -> type instanceof WorkspaceFileType));
  }

  public void testFileTypeBeanByFileName() {
    assertInstanceOf(myFileTypeManager.getFileTypeByFileName("foo.iws"), WorkspaceFileType.class);
  }

  public void testFileTypeBeanByExtensionWithFieldName() {
    assertSame(ModuleFileType.INSTANCE, myFileTypeManager.getFileTypeByExtension("iml"));
  }

  public void testIsFileTypeRunsDetector() throws IOException {
    VirtualFile vFile = createTempVirtualFile("x.bbb", null, "#!archive!!!", StandardCharsets.UTF_8);

    AtomicInteger detectorCalls = new AtomicInteger();
    ExtensionTestUtil.maskExtensions(FileTypeRegistry.FileTypeDetector.EP_NAME, Collections.singletonList(new FileTypeRegistry.FileTypeDetector() {
        @Nullable
        @Override
        public FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText) {
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

    assertEquals(ArchiveFileType.INSTANCE, vFile.getFileType());
    assertEquals(ArchiveFileType.INSTANCE, vFile.getFileType());
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
    List<String> strings = StringUtil.split(FileTypeManagerImpl.DEFAULT_IGNORED, ";");
    String sorted = strings.stream().sorted().collect(Collectors.joining(";"));
    for (int i = 0; i < strings.size(); i++) {
      String string = strings.get(i);
      String prev = i == 0 ? "" : strings.get(i - 1);
      assertTrue("FileTypeManagerImpl.DEFAULT_IGNORED must be sorted, but got: '" + prev + "' >= '" + string + "'. This would be better:\n" + sorted, prev.compareTo(string) < 0);
    }
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

  @NotNull
  private static Disposable registerFileType(@NotNull FileTypeBean bean, @NotNull PluginDescriptor pluginDescriptor) {
    bean.setPluginDescriptor(pluginDescriptor);
    Disposable disposable = Disposer.newDisposable();
    WriteAction.run(() -> FileTypeManagerImpl.EP_NAME.getPoint().registerExtension(bean, disposable));
    return disposable;
  }

  public void testRegisterUnregisterExtensionWithFileName() throws IOException {
    String name = ".veryWeirdFileName";
    File tempFile = createTempFile(name, "This is a text file");
    VirtualFile vFile = getVirtualFile(tempFile);
    assertEquals(PlainTextFileType.INSTANCE, vFile.getFileType());

    FileTypeBean bean = new FileTypeBean();
    bean.name = MyTestFileType.NAME;
    bean.fileNames = name;
    bean.implementationClass = MyTestFileType.class.getName();
    Disposable disposable = registerFileType(bean, FileTypeManagerImpl.coreIdeaPluginDescriptor());
    try {
      clearFileTypeCache();

      assertEquals(MyTestFileType.NAME, myFileTypeManager.getFileTypeByFileName(name).getName());
      assertEquals(MyTestFileType.NAME, vFile.getFileType().getName());
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
    initStandardFileTypes();
    String name = ".veryUnknownFile";
    File tempFile = createTempFile(name, "This is a text file");
    VirtualFile vFile = getVirtualFile(tempFile);
    assertEquals(PlainTextFileType.INSTANCE, vFile.getFileType());

    FileTypeBean bean = new FileTypeBean();
    bean.name = "XML";
    bean.fileNames = name;
    Disposable disposable = registerFileType(bean, FileTypeManagerImpl.coreIdeaPluginDescriptor());
    try {
      clearFileTypeCache();

      assertEquals("XML", myFileTypeManager.getFileTypeByFileName(name).getName());
      assertEquals("XML", vFile.getFileType().getName());
    }
    finally {
      WriteAction.run(() -> Disposer.dispose(disposable));
    }

    assertEquals("UNKNOWN", myFileTypeManager.getFileTypeByFileName(name).getName());
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
    myFileTypeManager.registerFileType(old);
  }

  private static class MyCustomImageFileType implements FileType {
    private MyCustomImageFileType() {
    }

    @NotNull
    @Override
    public String getName() {
      return "myimage";
    }

    @Override
    public @NotNull String getDisplayName() {
      return getClass().getName();
    }

    @Override
    public @NotNull String getDescription() {
      return getDisplayName();
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
      return "hs";
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
  }

  private static class MyCustomImageFileType2 extends MyCustomImageFileType {
    @Override
    public @NotNull String getName() {
      return super.getName()+"2";
    }
  }

  public void testPluginWhichOverridesBundledFileTypeMustWin() {
    FileType bundled = Objects.requireNonNull(myFileTypeManager.findFileTypeByName("Image"));
    PluginDescriptor pluginDescriptor = PluginManagerCore.getPluginDescriptorOrPlatformByClassName(bundled.getClass().getName());
    assertTrue(pluginDescriptor.isBundled());
    LOG.debug("pluginDescriptor = " + pluginDescriptor);

    FileTypeBean bean = new FileTypeBean();
    bean.name = new MyCustomImageFileType().getName();
    String ext = myFileTypeManager.getAssociatedExtensions(bundled)[0];
    bean.extensions = ext;
    bean.implementationClass = MyCustomImageFileType.class.getName();

    bean.setPluginDescriptor(new DefaultPluginDescriptor(PluginId.getId("myTestPlugin"), PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID).getPluginClassLoader()));
    Disposable disposable = Disposer.newDisposable();
    Disposer.register(getTestRootDisposable(), disposable);
    WriteAction.run(() -> FileTypeManagerImpl.EP_NAME.getPoint().registerExtension(bean, disposable));

    assertInstanceOf(myFileTypeManager.findFileTypeByName(bean.name), MyCustomImageFileType.class);
    assertInstanceOf(myFileTypeManager.getFileTypeByExtension(ext), MyCustomImageFileType.class);

    WriteAction.run(() -> Disposer.dispose(disposable));
  }

  private static void assertFileTypeIsUnregistered(@NotNull FileType fileType) {
    for (FileType type : FileTypeManager.getInstance().getRegisteredFileTypes()) {
      if (type.getClass() == fileType.getClass()) {
        throw new AssertionError(type + " is still registered");
      }
    }
    FileType registered = FileTypeManager.getInstance().findFileTypeByName(fileType.getName());
    if (registered != null && !(registered instanceof AbstractFileType)) {
      fail(registered.toString());
    }
  }

  public void testTwoPluginsWhichOverrideBundledFileTypeMustNegotiateBetweenThemselves() {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType bundled = Objects.requireNonNull(fileTypeManager.findFileTypeByName("Image"));
    PluginDescriptor pluginDescriptor = Objects.requireNonNull(PluginManagerCore.getPluginDescriptorOrPlatformByClassName(bundled.getClass().getName()));
    assertTrue(pluginDescriptor.isBundled());
    LOG.debug("pluginDescriptor = " + pluginDescriptor);

    String ext = fileTypeManager.getAssociatedExtensions(bundled)[0];
    FileTypeBean bean = new FileTypeBean();
    bean.name = new MyCustomImageFileType().getName();
    bean.extensions = ext;
    bean.implementationClass = MyCustomImageFileType.class.getName();
    bean.setPluginDescriptor(new DefaultPluginDescriptor(PluginId.getId("myTestPlugin"), FileTypeManagerImpl.coreIdeaPluginDescriptor().getPluginClassLoader()));

    FileTypeBean bean2 = new FileTypeBean();
    bean2.name = new MyCustomImageFileType2().getName();
    bean2.extensions = ext;
    bean2.implementationClass = MyCustomImageFileType2.class.getName();
    bean2.setPluginDescriptor(new DefaultPluginDescriptor(PluginId.getId("myTestPlugin2"), FileTypeManagerImpl.coreIdeaPluginDescriptor().getPluginClassLoader()));

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
    VirtualFile file0 = createTempVirtualFile("x.xxxx", null, "#!/usr/bin/gogogo\na=b", StandardCharsets.UTF_8);
    assertEquals(PlainTextFileType.INSTANCE, file0.getFileType());
    FileTypeManagerImpl.FileTypeWithDescriptor fileType = FileTypeManagerImpl.coreDescriptorFor(StdFileTypes.PROPERTIES);
    myFileTypeManager.getExtensionMap().addHashBangPattern("gogogo", fileType);
    try {
      VirtualFile file = createTempVirtualFile("x.xxxx", null, "#!/usr/bin/gogogo\na=b", StandardCharsets.UTF_8);
      assertEquals(StdFileTypes.PROPERTIES, file.getFileType());
    }
    finally {
      myFileTypeManager.getExtensionMap().removeHashBangPattern("gogogo", fileType);
    }
  }

  private static class MyTestFileType implements FileType {
    public static final String NAME = "Foo files";
    public static final String EXTENSION = "fromPlugin";

    private MyTestFileType() {
    }

    @NotNull
    @Override
    public String getName() {
      return NAME;
    }

    @NotNull
    @Override
    public String getDescription() {
      return "";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
      return EXTENSION;
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
  }

  private static class MyHaskellFileType implements FileType {
    public static final String NAME = "Haskell";

    private MyHaskellFileType() {
    }

    @NotNull
    @Override
    public String getName() {
      return NAME;
    }

    @NotNull
    @Override
    public String getDescription() {
      return "";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
      return "hs";
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
  }

  public void testFileTypeConstructorsMustBeNonPublic() {
    FileType[] fileTypes = myFileTypeManager.getRegisteredFileTypes();
    LOG.debug("Registered file types: "+fileTypes.length);
    for (FileType fileType : fileTypes) {
      if (fileType.getClass() == AbstractFileType.class) continue;
      Constructor<?>[] constructors = fileType.getClass().getDeclaredConstructors();
      for (Constructor<?> constructor : constructors) {
        assertFalse("FileType constructor must be non-public to avoid duplicates but got: " + constructor, Modifier.isPublic(constructor.getModifiers()));
      }
    }
  }

  public void testDetectedAsTextMustNotStuckWithUnknownFileTypeWhenShrinkedToZeroLength() throws IOException {
    File f = createTempFile("xx.lkjlkjlkjlj", "a");
    VirtualFile virtualFile = getVirtualFile(f);
    assertEquals(PlainTextFileType.INSTANCE, virtualFile.getFileType());

    setBinaryContent(virtualFile, new byte[0]);
    myFileTypeManager.drainReDetectQueue();
    assertEquals(UnknownFileType.INSTANCE, virtualFile.getFileType());

    setBinaryContent(virtualFile, "qwe\newq".getBytes(StandardCharsets.UTF_8));
    myFileTypeManager.drainReDetectQueue();
    assertEquals(PlainTextFileType.INSTANCE, virtualFile.getFileType());
  }

  public void testRegisterFileTypesWithIdenticalDisplayNameOrDescriptionMustThrow() {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());

    Disposable disposable = Disposer.newDisposable();
    try {
      createFakeType("myCreativeName0", "display1", "descr1", "ext1", disposable);
      createFakeType("myCreativeName1", "display1", "descr2", "ext2", disposable);
      assertThrows(Throwable.class, () -> initStandardFileTypes());
      myFileTypeManager.unregisterFileType(myFileTypeManager.findFileTypeByName("myCreativeName0"));
      myFileTypeManager.unregisterFileType(myFileTypeManager.findFileTypeByName("myCreativeName1"));
      createFakeType("myCreativeName2", "display0", "descr", "ext1", disposable);
      createFakeType("myCreativeName3", "display1", "descr", "ext2", disposable);
      assertThrows(Throwable.class, () -> initStandardFileTypes());
      myFileTypeManager.unregisterFileType(myFileTypeManager.findFileTypeByName("myCreativeName2"));
      myFileTypeManager.unregisterFileType(myFileTypeManager.findFileTypeByName("myCreativeName3"));
    }
    finally {
      Disposer.dispose(disposable);
    }
    initStandardFileTypes(); // give FileTypeManagerImpl chance to finish init standard file types without exceptions
  }

  @NotNull
  private static FileType createFakeType(@NotNull String name,
                                         @NotNull String displayName,
                                         @NotNull String description,
                                         @NotNull String extension,
                                         @NotNull Disposable disposable) {
    FileType myType = new FakeFileType() {
      @Override
      public boolean isMyFileType(@NotNull VirtualFile file) {
        return false;
      }

      @Override
      public @NotNull @NonNls String getName() {
        return name;
      }

      @Nls
      @Override
      public @NotNull String getDisplayName() {
        return displayName;
      }

      @Override
      public @NotNull @NlsContexts.Label String getDescription() {
        return description;
      }
    };
    FileTypeFactory.FILE_TYPE_FACTORY_EP.getPoint().registerExtension(new FileTypeFactory() {
      @Override
      public void createFileTypes(@NotNull FileTypeConsumer consumer) {
        consumer.consume(myType, extension);
      }
    }, disposable);
    return myType;
  }

  public void testDetectorMustWorkForEmptyFileNow() throws IOException {
    Set<VirtualFile> detectorCalled = ContainerUtil.newConcurrentSet();
    String magicName = "blah-blah.todetect";
    FileTypeRegistry.FileTypeDetector detector = (file, __, __0) -> {
      detectorCalled.add(file);
      if (file.getName().equals(magicName)) {
        return new MyTestFileType();
      }
      return null;
    };
    runWithDetector(detector, () -> {
      VirtualFile vFile = createTempVirtualFile(magicName, null, "", StandardCharsets.UTF_8);
      ensureRedetected(vFile, detectorCalled);
      assertTrue(vFile.getFileType().toString(), vFile.getFileType() instanceof MyTestFileType);
    });
  }

  public void testDuplicateNativeFileType() {
    FileTypeFactory.FILE_TYPE_FACTORY_EP.getPoint().registerExtension(new FileTypeFactory() {
      @Override
      public void createFileTypes(@NotNull FileTypeConsumer consumer) {
        consumer.consume(new FakeFileType() {
          @Override
          public boolean isMyFileType(@NotNull VirtualFile file) {
            return false;
          }

          @Override
          public @NotNull String getName() {
            return "Foo";
          }

          @Override
          public @NotNull String getDescription() {
            return "Foo";
          }
        }, "chm;xyz");
      }
    }, getTestRootDisposable());
    try {
      myFileTypeManager.initStandardFileTypes();
      assertEquals("Foo", myFileTypeManager.getFileTypeByFileName("foo.xyz").getName());
    }
    finally {
      myFileTypeManager.unregisterFileType(myFileTypeManager.findFileTypeByName("Foo"));
    }
  }
}
