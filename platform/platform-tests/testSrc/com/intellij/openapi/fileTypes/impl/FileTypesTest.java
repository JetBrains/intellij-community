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
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.PatternUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
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
    FileTypeManagerImpl.reDetectAsync(false);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myFileTypeManager.setIgnoredFilesList(myOldIgnoredFilesList);
      }
    });
    super.tearDown();
  }

  public void testMaskExclude() {
    final String pattern1 = "a*b.c?d";
    final String pattern2 = "xxx";
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myFileTypeManager.setIgnoredFilesList(pattern1 + ";" + pattern2);
      }
    });
    checkIgnored("ab.cxd");
    checkIgnored("axb.cxd");
    checkIgnored("xxx");
    checkNotIgnored("ax.cxx");
    checkNotIgnored("ab.cd");
    checkNotIgnored("ab.c__d");
    checkNotIgnored("xx" + "xx");
    checkNotIgnored("xx");
    assertTrue(myFileTypeManager.isIgnoredFilesListEqualToCurrent(pattern2 + ";" + pattern1));
    assertFalse(myFileTypeManager.isIgnoredFilesListEqualToCurrent(pattern2 + ";" + "ab.c*d"));
  }

  public void testExcludePerformance() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myFileTypeManager.setIgnoredFilesList("1*2;3*4;5*6;7*8;9*0;*1;*3;*5;*6;7*;*8*");
      }
    });
    final String[] names = new String[100];
    for (int i = 0; i < names.length; i++) {
      String name = String.valueOf((i%10)*10 + (i*100) + i + 1);
      names[i] = (name + name + name + name);
    }
    PlatformTestUtil.startPerformanceTest("ignore perf", 700, new ThrowableRunnable() {
      @Override
      public void run() throws Throwable {
        for (int i=0;i<1000;i++) {
          for (String name : names) {
            myFileTypeManager.isFileIgnored(name);
          }
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

  public void testAddNewExtension() throws Exception {
    FileTypeAssocTable<FileType> associations = new FileTypeAssocTable<FileType>();
    associations.addAssociation(FileTypeManager.parseFromString("*.java"), FileTypes.ARCHIVE);
    associations.addAssociation(FileTypeManager.parseFromString("*.xyz"), StdFileTypes.XML);
    associations.addAssociation(FileTypeManager.parseFromString("SomeSpecial*.java"), StdFileTypes.XML); // patterns should have precedence over extensions
    assertEquals(StdFileTypes.XML, associations.findAssociatedFileType("sample.xyz"));
    assertEquals(StdFileTypes.XML, associations.findAssociatedFileType("SomeSpecialFile.java"));
    checkNotAssociated(StdFileTypes.XML, "java", associations);
    checkNotAssociated(StdFileTypes.XML, "iws", associations);
  }

  public void testIgnoreOrder() {
    final FileTypeManagerEx manager = FileTypeManagerEx.getInstanceEx();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        manager.setIgnoredFilesList("a;b;");
      }
    });
    assertEquals("a;b;", manager.getIgnoredFilesList());
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        manager.setIgnoredFilesList("b;a;");
      }
    });
    assertEquals("b;a;", manager.getIgnoredFilesList());
  }

  public void testIgnoredFiles() throws IOException {
    File file = createTempFile(".svn", "");
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertTrue(FileTypeManager.getInstance().isFileIgnored(vFile));
    vFile.delete(this);

    file = createTempFile("a.txt", "");
    vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
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
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(virtualFile);
    PsiFile psi = getPsiManager().findFile(virtualFile);
    assertTrue(psi instanceof PsiPlainTextFile);
    assertEquals(FileTypes.PLAIN_TEXT, virtualFile.getFileType());
  }

  public void testAutoDetectedWhenDocumentWasCreated() throws IOException {
    File dir = createTempDirectory();
    File file = FileUtil.createTempFile(dir, "x", "xxx_xx_xx", true);
    FileUtil.writeToFile(file, "xxx xxx xxx xxx");
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(virtualFile);
    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    assertNotNull(document);
    assertEquals(FileTypes.PLAIN_TEXT, virtualFile.getFileType());
  }

  public void testAutoDetectionShouldNotBeOverEager() throws IOException {
    File dir = createTempDirectory();
    File file = FileUtil.createTempFile(dir, "x", "xxx_xx_xx", true);
    FileUtil.writeToFile(file, "xxx xxx xxx xxx");
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(virtualFile);
    TestCase.assertEquals(PlainTextFileType.INSTANCE, virtualFile.getFileType());
  }

  public void testAutoDetectEmptyFile() throws IOException {
    File dir = createTempDirectory();
    File file = FileUtil.createTempFile(dir, "x", "xxx_xx_xx", true);
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(virtualFile);
    assertEquals(FileTypes.UNKNOWN, virtualFile.getFileType());
    PsiFile psi = getPsiManager().findFile(virtualFile);
    assertTrue(psi instanceof PsiBinaryFile);
    assertEquals(FileTypes.UNKNOWN, virtualFile.getFileType());

    virtualFile.setBinaryContent("xxxxxxx".getBytes(CharsetToolkit.UTF8_CHARSET));
    assertEquals(FileTypes.PLAIN_TEXT, virtualFile.getFileType());
    PsiFile after = getPsiManager().findFile(virtualFile);
    assertNotSame(psi, after);
    assertFalse(psi.isValid());
    assertTrue(after.isValid());
    assertTrue(after instanceof PsiPlainTextFile);
  }

  public void testAutoDetectTextFileFromContents() throws IOException {
    File dir = createTempDirectory();
    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    VirtualFile vFile = vDir.createChildData(this, "test.xxxxxxxx");
    VfsUtil.saveText(vFile, "text");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    assertEquals(PlainTextFileType.INSTANCE, vFile.getFileType()); // type autodetected during indexing

    PsiFile psiFile = ((PsiManagerEx)PsiManager.getInstance(getProject())).getFileManager().findFile(vFile); // autodetect text file if needed
    assertNotNull(psiFile);
    assertEquals(PlainTextFileType.INSTANCE, psiFile.getFileType());
  }

  public void testAutoDetectTextFileEvenOutsideTheProject() throws IOException {
    File d = createTempDirectory();
    File f = new File(d, "xx.asfdasdfas");
    FileUtil.writeToFile(f, "asdasdasdfafds");
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);

    assertEquals(PlainTextFileType.INSTANCE, vFile.getFileType());
  }

  public void test7BitBinaryIsNotText() throws IOException {
    File d = createTempDirectory();
    File f = new File(d, "xx.asfdasdfas");
    byte[] bytes = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 'x', 'a', 'b'};
    assertEquals(new CharsetToolkit(bytes).guessFromContent(bytes.length), CharsetToolkit.GuessedEncoding.BINARY);
    FileUtil.writeToFile(f, bytes);

    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);

    assertEquals(UnknownFileType.INSTANCE, vFile.getFileType());
  }

  public void test7BitIsText() throws IOException {
    File d = createTempDirectory();
    File f = new File(d, "xx.asfdasdfas");
    byte[] bytes = {9, 10, 13, 'x', 'a', 'b'};
    assertEquals(new CharsetToolkit(bytes).guessFromContent(bytes.length), CharsetToolkit.GuessedEncoding.SEVEN_BIT);
    FileUtil.writeToFile(f, bytes);
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);

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
        log("T: my detector run for "+file.getName()+"; result: "+(result == null ? null : result.getName()));
        return result;
      }

      @Override
      public int getVersion() {
        return 0;
      }
    };
    Extensions.getRootArea().getExtensionPoint(FileTypeRegistry.FileTypeDetector.EP_NAME).registerExtension(detector);
    try {
      log("T: ------");
      File f = createTempFile("xx.asfdasdfas", "akjdhfksdjgf");
      VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
      ensureRedetected(vFile, detectorCalled);
      assertTrue(vFile.getFileType().toString(), vFile.getFileType() instanceof PlainTextFileType);

      log("T: ------");
      VfsUtil.saveText(vFile, "TYPE:IDEA_MODULE");
      ensureRedetected(vFile, detectorCalled);
      assertTrue(vFile.getFileType().toString(), vFile.getFileType() instanceof ModuleFileType);

      log("T: ------");
      VfsUtil.saveText(vFile, "TYPE:IDEA_PROJECT");
      ensureRedetected(vFile, detectorCalled);
      assertTrue(vFile.getFileType().toString(), vFile.getFileType() instanceof ProjectFileType);
      log("T: ------");
    }
    finally {
      Extensions.getRootArea().getExtensionPoint(FileTypeRegistry.FileTypeDetector.EP_NAME).unregisterExtension(detector);
    }
  }

  private static void log(String message) {
    System.out.println(message);
  }

  private void ensureRedetected(VirtualFile vFile, Set<VirtualFile> detectorCalled) {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    log("T: ensureRedetected: commit. queue: "+myFileTypeManager.dumpReDetectQueue());
    UIUtil.dispatchAllInvocationEvents();
    log("T: ensureRedetected: dispatch. queue: "+ myFileTypeManager.dumpReDetectQueue());
    myFileTypeManager.drainReDetectQueue();
    log("T: ensureRedetected: drain. queue: "+myFileTypeManager.dumpReDetectQueue());
    UIUtil.dispatchAllInvocationEvents();
    log("T: ensureRedetected: dispatch");
    FileType type = vFile.getFileType();
    log("T: ensureRedetected: getFileType ("+type.getName()+")");
    assertTrue(detectorCalled.contains(vFile));
    detectorCalled.clear();
    log("T: ensureRedetected: clear");
  }

  public void testReassignedPredefinedFileType() throws Exception {
    FileType perlFileType = myFileTypeManager.getFileTypeByFileName("foo.pl");
    assertEquals("Perl", perlFileType.getName());
    assertEquals(PlainTextFileType.INSTANCE, myFileTypeManager.getFileTypeByFileName("foo.cgi"));
    myFileTypeManager.associatePattern(perlFileType, "*.cgi");
    assertEquals(perlFileType, myFileTypeManager.getFileTypeByFileName("foo.cgi"));

    Element element = myFileTypeManager.getState();
    myFileTypeManager.loadState(element);
    myFileTypeManager.initComponent();
    assertEquals(perlFileType, myFileTypeManager.getFileTypeByFileName("foo.cgi"));

    myFileTypeManager.removeAssociatedExtension(perlFileType, "*.cgi");
    myFileTypeManager.clearForTests();
    myFileTypeManager.initStandardFileTypes();
    myFileTypeManager.initComponent();
  }

  public void testRenamedPropertiesToUnknownAndBack() throws Exception {
    FileType propFileType = myFileTypeManager.getFileTypeByFileName("xx.properties");
    assertEquals("Properties", propFileType.getName());
    File file = createTempFile("xx.properties", "xx=yy");
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertEquals(propFileType, myFileTypeManager.getFileTypeByFile(vFile));

    rename(vFile, "xx.zxmcnbzmxnbc");
    UIUtil.dispatchAllInvocationEvents();
    assertEquals(PlainTextFileType.INSTANCE, myFileTypeManager.getFileTypeByFile(vFile));

    rename(vFile, "xx.properties");
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
    myFileTypeManager.associatePattern(PlainTextFileType.INSTANCE, "*.fromPlugin");
    Element element = myFileTypeManager.getState();
    String s = JDOMUtil.writeElement(element);
    log(s);

    final AbstractFileType typeFromPlugin = new AbstractFileType(new SyntaxTable());
    PlatformTestUtil.registerExtension(FileTypeFactory.FILE_TYPE_FACTORY_EP, new FileTypeFactory() {
      @Override
      public void createFileTypes(@NotNull FileTypeConsumer consumer) {
        consumer.consume(typeFromPlugin, "fromPlugin");
      }
    }, getTestRootDisposable());
    myFileTypeManager.initStandardFileTypes();
    myFileTypeManager.loadState(element);

    myFileTypeManager.initComponent();
    Map<FileNameMatcher, Pair<FileType, Boolean>> mappings = myFileTypeManager.getRemovedMappings();
    assertEquals(1, mappings.size());
    assertEquals(typeFromPlugin, mappings.values().iterator().next().first);
  }

  public void testPreserveUninstalledPluginAssociations() throws Exception {
    final AbstractFileType typeFromPlugin = new AbstractFileType(new SyntaxTable()) {
      @NotNull
      @Override
      public String getName() {
        return "Foo files";
      }

      @Override
      public boolean isReadOnly() {
        return true; // prevents from serialization
      }
    };
    FileTypeFactory factory = new FileTypeFactory() {
      @Override
      public void createFileTypes(@NotNull FileTypeConsumer consumer) {
        consumer.consume(typeFromPlugin, "fromPlugin");
      }
    };
    Element element = myFileTypeManager.getState();
    try {
      Extensions.getRootArea().getExtensionPoint(FileTypeFactory.FILE_TYPE_FACTORY_EP).registerExtension(factory);
      myFileTypeManager.initStandardFileTypes();
      myFileTypeManager.loadState(element);
      myFileTypeManager.initComponent();

      myFileTypeManager.associatePattern(typeFromPlugin, "*.foo");

      element = myFileTypeManager.getState();
      log(JDOMUtil.writeElement(element));

      Extensions.getRootArea().getExtensionPoint(FileTypeFactory.FILE_TYPE_FACTORY_EP).unregisterExtension(factory);
      myFileTypeManager.clearForTests();
      myFileTypeManager.initStandardFileTypes();
      myFileTypeManager.loadState(element);
      myFileTypeManager.initComponent();

      element = myFileTypeManager.getState();
      log(JDOMUtil.writeElement(element));

      Extensions.getRootArea().getExtensionPoint(FileTypeFactory.FILE_TYPE_FACTORY_EP).registerExtension(factory);
      myFileTypeManager.clearForTests();
      myFileTypeManager.initStandardFileTypes();
      myFileTypeManager.loadState(element);
      myFileTypeManager.initComponent();

      element = myFileTypeManager.getState();
      log(JDOMUtil.writeElement(element));

      assertEquals(typeFromPlugin, myFileTypeManager.getFileTypeByFileName("foo.foo"));
    }
    finally {
      Extensions.getRootArea().getExtensionPoint(FileTypeFactory.FILE_TYPE_FACTORY_EP).unregisterExtension(factory);
    }
  }

  // IDEA-139409 Persistent message "File type recognized: File extension *.vm was reassigned to VTL"
  public void testReassign() throws Exception {
    Element element = JDOMUtil.loadDocument(
      "<component name=\"FileTypeManager\" version=\"13\">\n" +
      "   <extensionMap>\n" +
      "      <mapping ext=\"zip\" type=\"Velocity Template files\" />\n" +
      "   </extensionMap>\n" +
      "</component>").getRootElement();

    myFileTypeManager.loadState(element);
    myFileTypeManager.initComponent();
    Map<FileNameMatcher, Pair<FileType, Boolean>> mappings = myFileTypeManager.getRemovedMappings();
    assertEquals(1, mappings.size());
    assertEquals(ArchiveFileType.INSTANCE, mappings.values().iterator().next().first);
    mappings.clear();
    assertEquals(ArchiveFileType.INSTANCE, myFileTypeManager.getFileTypeByExtension("zip"));
    Element map = myFileTypeManager.getState().getChild("extensionMap");
    if (map != null) {
      fail(JDOMUtil.writeElement(map));
    }
  }

  public void testDefaultFileType() throws Exception {
    FileType idl = myFileTypeManager.findFileTypeByName("IDL");
    myFileTypeManager.associatePattern(idl, "*.xxx");
    Element element = myFileTypeManager.getState();
    log(JDOMUtil.writeElement(element));
    myFileTypeManager.removeAssociatedExtension(idl, "xxx");
    myFileTypeManager.clearForTests();
    myFileTypeManager.initStandardFileTypes();
    myFileTypeManager.loadState(element);
    myFileTypeManager.initComponent();
    FileType extensions = myFileTypeManager.getFileTypeByExtension("xxx");
    assertEquals("IDL", extensions.getName());
  }
}
