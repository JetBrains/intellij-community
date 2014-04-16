/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.PatternUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

public class FileTypesTest extends PlatformTestCase {
  private FileTypeManagerImpl myFileTypeManager;
  private String myOldIgnoredFilesList;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFileTypeManager = (FileTypeManagerImpl)FileTypeManagerEx.getInstanceEx();
    myOldIgnoredFilesList = myFileTypeManager.getIgnoredFilesList();
  }

  @Override
  protected void tearDown() throws Exception {
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
    runPerformanceTest(true);
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

  private void runPerformanceTest(boolean rerunOnOvertime) {
    long startTime = System.currentTimeMillis();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myFileTypeManager.setIgnoredFilesList("1*2;3*4;5*6;7*8;9*0;*1;*3;*5;*6;7*;*8*");
      }
    });
    for (int i = 0; i < 100; i++) {
      String name = String.valueOf((i%10)*10 + (i*100) + i + 1);
      myFileTypeManager.isFileIgnored(name + name + name + name);
    }
    long time = System.currentTimeMillis() - startTime;
    if (time > 700) {
      if (rerunOnOvertime) runPerformanceTest(false);
      else fail("Time=" + time);
    }
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
    FileType fileType = FileTypeRegistry.getInstance().getFileTypeByFileName("x" + ModuleFileType.DOT_DEFAULT_EXTENSION);
    assertTrue(fileType.toString(), fileType instanceof ModuleFileType);
    fileType = FileTypeRegistry.getInstance().getFileTypeByFileName("x" + ProjectFileType.DOT_DEFAULT_EXTENSION);
    assertTrue(fileType.toString(), fileType instanceof ProjectFileType);

    FileTypeRegistry.FileTypeDetector detector = new FileTypeRegistry.FileTypeDetector() {
      @Nullable
      @Override
      public FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText) {
        String text = firstCharsIfText.toString();
        if (text.startsWith("TYPE:")) return FileTypeRegistry.getInstance().findFileTypeByName(StringUtil.trimStart(text, "TYPE:"));
        return null;
      }

      @Override
      public int getVersion() {
        return 0;
      }
    };
    Extensions.getRootArea().getExtensionPoint(FileTypeRegistry.FileTypeDetector.EP_NAME).registerExtension(detector);
    try {
      File d = createTempDirectory();
      File f = new File(d, "xx.asfdasdfas");
      FileUtil.writeToFile(f, "akjdhfksdjgf");
      VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
      assertTrue(vFile.getFileType().toString(), vFile.getFileType() instanceof PlainTextFileType);

      VfsUtil.saveText(vFile, "TYPE:IDEA_MODULE");
      myFileTypeManager.drainReDetectQueue();
      assertTrue(vFile.getFileType().toString(), vFile.getFileType() instanceof ModuleFileType);

      VfsUtil.saveText(vFile, "TYPE:IDEA_PROJECT");
      myFileTypeManager.drainReDetectQueue();
      assertTrue(vFile.getFileType().toString(), vFile.getFileType() instanceof ProjectFileType);
    }
    finally {
      Extensions.getRootArea().getExtensionPoint(FileTypeRegistry.FileTypeDetector.EP_NAME).unregisterExtension(detector);
    }
  }
}
