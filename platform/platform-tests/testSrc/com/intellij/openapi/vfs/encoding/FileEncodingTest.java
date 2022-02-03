// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.encoding;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.impl.status.EncodingPanel;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.copy.CopyFilesOrDirectoriesHandler;
import com.intellij.testFramework.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.ByteArrayCharSequence;
import com.intellij.util.text.XmlCharsetDetector;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import javax.swing.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;

public class FileEncodingTest extends HeavyPlatformTestCase implements TestDialog {
  private static final Charset US_ASCII = StandardCharsets.US_ASCII;
  private static final Charset WINDOWS_1251 = CharsetToolkit.WIN_1251_CHARSET;
  private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
  private static final String UTF8_XML_PROLOG = prolog(StandardCharsets.UTF_8);
  private static final byte[] NO_BOM = ArrayUtilRt.EMPTY_BYTE_ARRAY;
  @org.intellij.lang.annotations.Language("XML")
  private static final String XML_TEST_BODY = "<web-app>\n" + "<!--\u043f\u0430\u043f\u0430-->\n" + "</web-app>";
  private static final String THREE_RUSSIAN_LETTERS = "\u043F\u0440\u0438\u0432\u0435\u0442";

  private TestDialog myOldTestDialogValue;

  @Override
  public int show(@NotNull String message) {
    return 0;
  }

  private static String prolog(@NotNull Charset charset) {
    return "<?xml version=\"1.0\" encoding=\"" + charset.name() + "\"?>\n";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myOldTestDialogValue = TestDialogManager.setTestDialog(this);
    EncodingProjectManager.getInstance(getProject()).setDefaultCharsetName(defaultProjectEncoding().name());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      TestDialogManager.setTestDialog(myOldTestDialogValue);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private static Document getDocument(VirtualFile file) {
    return FileDocumentManager.getInstance().getDocument(file);
  }

  public void testWin1251() {
    VirtualFile vTestRoot = getTestRoot();
    VirtualFile xml = vTestRoot.findChild("xWin1251.xml");

    String expected = prolog(WINDOWS_1251) + XML_TEST_BODY;
    String text = getDocument(xml).getText();

    assertEquals(expected, text);
  }

  public void testXmlProlog() throws IOException {
    VirtualFile vTestRoot = getTestRoot();
    VirtualFile xml = Objects.requireNonNull(vTestRoot.findChild("xNotepadUtf8.xml"));

    String expected = UTF8_XML_PROLOG + XML_TEST_BODY;
    String text = getDocument(xml).getText();

    if (!expected.equals(text)) {
      System.err.print("expected = ");
      for (int i=0; i<50;i++) {
        char c = expected.charAt(i);
        System.err.print(Integer.toHexString(c) + ", ");
      }
      System.err.println();
      System.err.print("expected bytes = ");
      byte[] expectedBytes = FileUtil.loadFileBytes(new File(xml.getPath()));
      for (int i=0; i<50;i++) {
        byte c = expectedBytes[i];
        System.err.print(Integer.toHexString(c) + ", ");
      }
      System.err.println();

      System.err.print("text = ");
      for (int i=0; i<50;i++) {
        char c = text.charAt(i);
        System.err.print(Integer.toHexString(c) + ", ");
      }
      System.err.println();
      System.err.print("text bytes = ");
      byte[] textBytes = xml.contentsToByteArray();
      for (int i=0; i<50;i++) {
        byte c = textBytes[i];
        System.err.print(Integer.toHexString(c) + ", ");
      }
      System.err.println();
      String charsetFromProlog = XmlCharsetDetector.extractXmlEncodingFromProlog(xml.contentsToByteArray());
      System.err.println("charsetFromProlog = " + charsetFromProlog);
      Charset charset = xml.getCharset();
      System.err.println("charset = " + charset);
    }
    assertEquals(expected, text);
  }

  public void testChangeToUtfProlog() throws IOException {
    VirtualFile src = find("xWin1251.xml");
    File dir = createTempDirectory();
    File file = new File(dir, "copy.xml");
    FileUtil.copy(new File(src.getPath()), file);

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      VirtualFile xml = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
      Document document = getDocument(xml);

      setText(document, UTF8_XML_PROLOG + XML_TEST_BODY);
      FileDocumentManager.getInstance().saveAllDocuments();

      byte[] savedBytes = FileUtil.loadFileBytes(file);
      String saved = new String(savedBytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
      String expected = (UTF8_XML_PROLOG + XML_TEST_BODY).replace("\r\n", "\n");

      assertEquals(expected, saved);
    });
  }

  public void testDefaultHtml() {
    VirtualFile file = find("defaultHtml.html");

    assertEquals(EncodingProjectManager.getInstance(getProject()).getDefaultCharset(), file.getCharset());
  }

  public void testSevenBitTextMustBeDetectedAsDefaultProjectEncodingInsteadOfUsAscii() throws IOException {
    VirtualFile file = createTempFile("txt", NO_BOM, "xxx\nxxx", WINDOWS_1251);

    assertEquals(defaultProjectEncoding(), file.getCharset());
  }

  private static @NotNull VirtualFile find(String name) {
    return Objects.requireNonNull(getTestRoot().findChild(name));
  }

  public void testTrickyProlog() {
    VirtualFile xml = find("xTrickyProlog.xml");

    assertEquals("Big5", xml.getCharset().name());
  }

  public void testDefaultXml() {
    VirtualFile xml = find("xDefault.xml");

    assertEquals(StandardCharsets.UTF_8, xml.getCharset());
  }

  public void testIbm866() {
    VirtualFile xml = find("xIbm866.xml");

    String expected = prolog(Charset.forName("IBM866")) + XML_TEST_BODY;
    String text = getDocument(xml).getText();

    assertEquals(expected, text);
  }

  private static VirtualFile getTestRoot() {
    File testRoot = new File(PathManagerEx.getCommunityHomePath(), "platform/platform-tests/testData/vfs/encoding");
    return LocalFileSystem.getInstance().findFileByIoFile(testRoot);
  }

  public void testUTF16BOM() throws IOException {
    VirtualFile file = find(getTestName(false) + ".txt");
    File source = new File(file.getPath());
    byte[] bytesSource = FileUtil.loadFileBytes(source);
    assertTrue(Arrays.toString(bytesSource), CharsetToolkit.hasUTF16LEBom(bytesSource));
    assertEquals("[-1, -2, 31, 4, 64, 4, 56, 4]", Arrays.toString(bytesSource));

    Document document = getDocument(file);
    String text = document.getText();
    assertEquals("\u041f\u0440\u0438", text);

    Path copy = getTempDir().newPath("copy.txt");
    Files.createDirectories(copy.getParent());
    Files.copy(source.toPath(), copy);
    VirtualFile fileCopy = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(copy);
    document = getDocument(fileCopy);
    //assertTrue(CharsetToolkit.hasUTF16LEBom(fileCopy.getBOM()));

    setText(document, "\u04ab\u04cd\u04ef");
    FileDocumentManager.getInstance().saveAllDocuments();
    byte[] bytes = Files.readAllBytes(copy);
    assertTrue(Arrays.toString(bytes), CharsetToolkit.hasUTF16LEBom(bytes));
    assertEquals("[-1, -2, -85, 4, -51, 4, -17, 4]", Arrays.toString(bytes));
  }

  public void testMetaHttpEquivHtml() throws IOException {
    doHtmlTest("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1252\">",
               "<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\">");
  }

  public void testMetaCharsetHtml5() throws IOException {
    doHtmlTest("<meta charset =\"windows-1252\">",
               "<meta charset =\"utf-8\">");
  }

  private void doHtmlTest(@org.intellij.lang.annotations.Language("HTML") String metaWithWindowsEncoding,
                          @org.intellij.lang.annotations.Language("HTML") String metaWithUtf8Encoding) throws IOException {
    VirtualFile file =
      createTempFile("html", NO_BOM, "<html><head>" + metaWithWindowsEncoding + "</head>" + THREE_RUSSIAN_LETTERS + "</html>",
                     WINDOWS_1252);

    assertEquals(WINDOWS_1252, file.getCharset());

    Document document = getDocument(file);
    @org.intellij.lang.annotations.Language("HTML")
    String text = "<html><head>" + metaWithUtf8Encoding + "</head>" +
                  THREE_RUSSIAN_LETTERS +
                  "</html>";
    setText(document, text);
    FileDocumentManager.getInstance().saveAllDocuments();

    assertEquals(StandardCharsets.UTF_8, file.getCharset());
  }

  public void testHtmlEncodingPreferBOM() throws IOException {
    @org.intellij.lang.annotations.Language("HTML")
    String content = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1252\"></head>" +
                     THREE_RUSSIAN_LETTERS +
                     "</html>";
    VirtualFile file = createTempFile("html", CharsetToolkit.UTF8_BOM, content, WINDOWS_1252);
    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF8_BOM, file.getBOM());
  }

  public void testHtmlEncodingMustBeCaseInsensitive() throws IOException {
    @org.intellij.lang.annotations.Language("HTML")
    String content = "<html>\n" +
                     "<head>\n" +
                     "<meta http-equiv=\"content-type\" content=\"text/html;charset=us-ascii\"> \n" +
                     "</head>\n" +
                     "<body>\n" +
                     "xyz\n" +
                     "</body>\n" +
                     "</html>";
    VirtualFile file = createTempFile("html", NO_BOM, content, WINDOWS_1252);
    assertEquals(US_ASCII, file.getCharset());
  }
  public void testHtmlContentAttributeOrder() throws IOException {
    @org.intellij.lang.annotations.Language("HTML")
    String content = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"\n" +
                     "\t\t\"http://www.w3.org/TR/html4/loose.dtd\">\n" +
                     "<html> <head>\n" +
                     "\t<meta content=\"text/html;charset=US-ASCII\" http-equiv=\"Content-Type\">\n" +
                     "</head> </html>";
    VirtualFile file = createTempFile("html", NO_BOM, content, WINDOWS_1252);
    assertEquals(US_ASCII, file.getCharset());
  }

  private static void setText(Document document, String text) {
    ApplicationManager.getApplication().runWriteAction(() -> document.setText(text));
  }

  public void testXHtmlStuff() throws IOException {
    @org.intellij.lang.annotations.Language("HTML")
    String text = "<xxx>\n</xxx>";
    VirtualFile file = createTempFile("xhtml", NO_BOM, text, WINDOWS_1252);

    Document document = getDocument(file);
    setText(document, prolog(WINDOWS_1251) + document.getText());
    FileDocumentManager.getInstance().saveAllDocuments();
    assertEquals(WINDOWS_1251, file.getCharset());

    setText(document, prolog(US_ASCII) + "\n<xxx></xxx>");
    FileDocumentManager.getInstance().saveAllDocuments();
    assertEquals(US_ASCII, file.getCharset());

    text = prolog(WINDOWS_1252) + "\n<xxx>\n</xxx>";
    file = createTempFile("xhtml", NO_BOM, text, WINDOWS_1252);
    assertEquals(WINDOWS_1252, file.getCharset());
  }

  public void testSettingEncodingManually() throws IOException {
    String text = "xyz";
    VirtualFile file = createTempFile("txt", NO_BOM, text, StandardCharsets.UTF_8);
    assertEquals(defaultProjectEncoding(), file.getCharset());
    File ioFile = new File(file.getPath());

    EncodingProjectManager.getInstance(getProject()).setEncoding(file, WINDOWS_1251);
    UIUtil.dispatchAllInvocationEvents();

    Document document = getDocument(file);
    boolean[] changed = {false};
    document.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        changed[0] = true;
      }
    });

    EncodingProjectManager.getInstance(getProject()).setEncoding(file, StandardCharsets.UTF_8);
    UIUtil.dispatchAllInvocationEvents();
    assertNotNull(FileDocumentManager.getInstance().getCachedDocument(file));

    //text in the editor changed
    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(changed[0]);
    changed[0] = false;

    FileDocumentManager.getInstance().saveAllDocuments();
    byte[] bytes = FileUtil.loadFileBytes(ioFile);
    //file on disk is still windows_1251
    assertArrayEquals(text.getBytes(WINDOWS_1251), bytes);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      document.insertString(0, "x");
    });
    text = "x" + text;

    assertTrue(changed[0]);
    changed[0] = false;
    EncodingProjectManager.getInstance(getProject()).setEncoding(file, US_ASCII);
    UIUtil.dispatchAllInvocationEvents();
    assertEquals(US_ASCII, file.getCharset());
    UIUtil.dispatchAllInvocationEvents();

    assertTrue(changed[0]); //reloaded again

    //after 'save as US_ASCII' file on disk changed
    bytes = FileUtil.loadFileBytes(ioFile);
    assertArrayEquals(text.getBytes(US_ASCII), bytes);
  }

  private static @NotNull Charset defaultProjectEncoding() {
    // just for the sake of testing something different from utf-8
    return StandardCharsets.US_ASCII;
  }

  public void testCopyMove() throws IOException {
    File root = createTempDirectory();
    File dir1 = new File(root, "dir1");
    assertTrue(dir1.mkdir());
    File dir2 = new File(root, "dir2");
    assertTrue(dir2.mkdir());
    VirtualFile vDir1 = getVirtualFile(dir1);
    VirtualFile vDir2 = getVirtualFile(dir2);
    assertNotNull(dir1.getPath(), vDir1);
    assertNotNull(dir2.getPath(), vDir2);

    EncodingProjectManager.getInstance(getProject()).setEncoding(vDir1, WINDOWS_1251);
    EncodingProjectManager.getInstance(getProject()).setEncoding(vDir2, US_ASCII);
    UIUtil.dispatchAllInvocationEvents();

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      VirtualFile xxx = vDir1.createChildData(this, "xxx.txt");
      setFileText(xxx, THREE_RUSSIAN_LETTERS);
      assertEquals(WINDOWS_1251, xxx.getCharset());
      VirtualFile copied = xxx.copy(this, vDir2, "xxx2.txt");
      assertEquals(WINDOWS_1251, copied.getCharset());

      VirtualFile xus = vDir2.createChildData(this, "xxx-us.txt");
      setFileText(xus, THREE_RUSSIAN_LETTERS);
      assertEquals(US_ASCII, xus.getCharset());

      xus.move(this, vDir1);
      assertEquals(US_ASCII, xus.getCharset());
    });
  }

  public void testCopyNested() throws IOException {
    File root = createTempDirectory();
    File dir1 = new File(root, "dir1");
    assertTrue(dir1.mkdir());
    File dir2 = new File(root, "dir2");
    assertTrue(dir2.mkdir());
    VirtualFile vDir1 = getVirtualFile(dir1);
    VirtualFile vDir2 = getVirtualFile(dir2);
    assertNotNull(dir1.getPath(), vDir1);
    assertNotNull(dir2.getPath(), vDir2);

    EncodingProjectManager.getInstance(getProject()).setEncoding(vDir1, WINDOWS_1251);
    EncodingProjectManager.getInstance(getProject()).setEncoding(vDir2, US_ASCII);
    UIUtil.dispatchAllInvocationEvents();

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      VirtualFile winF = vDir1.createChildData(this, "xxx.txt");

      PsiDirectory psiDir1 = Objects.requireNonNull(PsiManager.getInstance(getProject()).findDirectory(vDir1));
      PsiDirectory psiDir2 = Objects.requireNonNull(PsiManager.getInstance(getProject()).findDirectory(vDir2));
      CopyFilesOrDirectoriesHandler.copyToDirectory(psiDir1, psiDir1.getName(), psiDir2);
      VirtualFile winFCopy = Objects.requireNonNull(psiDir2.getVirtualFile().findFileByRelativePath(psiDir1.getName() + "/" + winF.getName()));
      assertEquals(WINDOWS_1251, winFCopy.getCharset());
      VirtualFile dir1Copy = psiDir2.getVirtualFile().findChild(psiDir1.getName());
      assertEquals(WINDOWS_1251, EncodingProjectManager.getInstance(getProject()).getEncoding(dir1Copy, false));
      assertNull(EncodingProjectManager.getInstance(getProject()).getEncoding(winFCopy, false));
    });
  }

  public void testBOMPersistsAfterEditing() throws IOException {
    VirtualFile file = createTempFile("txt", CharsetToolkit.UTF8_BOM, XML_TEST_BODY, StandardCharsets.UTF_8);

    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF8_BOM, file.getBOM());

    Document document = getDocument(file);
    setText(document, "horseradish");
    FileDocumentManager.getInstance().saveAllDocuments();

    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF8_BOM, file.getBOM());

    byte[] bytes = FileUtil.loadFileBytes(VfsUtilCore.virtualToIoFile(file));
    assertTrue(CharsetToolkit.hasUTF8Bom(bytes));
  }

  public void testBOMPersistsAfterIndexRescan() throws IOException {
    VirtualFile file = createTempFile("txt", CharsetToolkit.UTF8_BOM, XML_TEST_BODY, StandardCharsets.UTF_8);

    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF8_BOM, file.getBOM());

    Document document = getDocument(file);
    String newContent = "horseradish";
    setText(document, newContent);

    FileDocumentManager.getInstance().saveAllDocuments();

    byte[] bytes = FileUtil.loadFileBytes(VfsUtilCore.virtualToIoFile(file));
    Charset charset = LoadTextUtil.detectCharsetAndSetBOM(file, bytes, file.getFileType());
    assertEquals(StandardCharsets.UTF_8, charset);
    assertArrayEquals(CharsetToolkit.UTF8_BOM, file.getBOM());

    assertTrue(CharsetToolkit.hasUTF8Bom(bytes));
  }

  public void testVFileGetInputStreamIsBOMAware() throws IOException {
    VirtualFile file = find("UTF16BOM.txt");

    String vfsLoad = VfsUtilCore.loadText(file);
    assertEquals("\u041f\u0440\u0438", vfsLoad);

    CharSequence ltuText = LoadTextUtil.loadText(file);
    assertEquals(vfsLoad, ltuText.toString());

    assertArrayEquals(CharsetToolkit.UTF16LE_BOM, file.getBOM());
  }
  public void testSetCharsetAfter() throws IOException {
    VirtualFile file = find("UTF16LE_NO_BOM.txt");
    file.setCharset(StandardCharsets.UTF_16LE);
    file.setBOM(null);
    String vfsLoad = VfsUtilCore.loadText(file);
    assertEquals("\u041f\u0440\u0438\u0432\u0435\u0442", vfsLoad);
    assertNull(file.getBOM());
  }

  public void testBOMResetAfterChangingUtf16ToUtf8() throws IOException {
    VirtualFile file = createTempFile("txt", CharsetToolkit.UTF16BE_BOM, THREE_RUSSIAN_LETTERS, StandardCharsets.UTF_16BE);

    assertEquals(StandardCharsets.UTF_16BE, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF16BE_BOM, file.getBOM());

    Document document = getDocument(file);
    String newContent = "horseradish";
    setText(document, newContent);
    FileDocumentManager.getInstance().saveAllDocuments();
    assertEquals(StandardCharsets.UTF_16BE, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF16BE_BOM, file.getBOM());

    EncodingUtil.saveIn(getProject(), document, null, file, StandardCharsets.UTF_8);

    byte[] bytes = FileUtil.loadFileBytes(VfsUtilCore.virtualToIoFile(file));

    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    assertNull(file.getBOM());

    assertFalse(CharsetToolkit.hasUTF8Bom(bytes));
  }

  public void testConvertNotAvailableForHtml() throws IOException {
    @org.intellij.lang.annotations.Language("HTML")
    String content = "<html><head><meta content=\"text/html; charset=utf-8\" http-equiv=\"content-type\"></head>" +
                     "<body>" + THREE_RUSSIAN_LETTERS +
                     "</body></html>";
    VirtualFile file = createTempFile("html", NO_BOM, content, StandardCharsets.UTF_8);
    Document document = FileDocumentManager.getInstance().getDocument(file);
    assertNotNull(document);
    FileDocumentManager.getInstance().saveAllDocuments();
    FileType fileType = file.getFileType();
    assertEquals(FileTypeManager.getInstance().getStdFileType("HTML"), fileType);
    Charset fromType = ((LanguageFileType)fileType).extractCharsetFromFileContent(myProject, file, (CharSequence)content);
    assertEquals(StandardCharsets.UTF_8, fromType);
    String fromProlog = XmlCharsetDetector.extractXmlEncodingFromProlog(content);
    assertNull(fromProlog);
    Charset charsetFromContent = EncodingManagerImpl.computeCharsetFromContent(file);
    assertEquals(StandardCharsets.UTF_8, charsetFromContent);
    EncodingUtil.FailReason result = EncodingUtil.checkCanConvert(file);
    assertEquals(EncodingUtil.FailReason.BY_FILE, result);
  }

  public void testConvertReload() throws IOException {
    VirtualFile file = createTempFile("txt", CharsetToolkit.UTF16BE_BOM, THREE_RUSSIAN_LETTERS, StandardCharsets.UTF_16BE);

    assertEquals(StandardCharsets.UTF_16BE, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF16BE_BOM, file.getBOM());

    Document document = getDocument(file);

    byte[] bytes = file.contentsToByteArray();
    String text = document.getText();
    Assert.assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, WINDOWS_1251));
    Assert.assertSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, US_ASCII));
    EncodingUtil.FailReason result = EncodingUtil.checkCanReload(file, null);
    assertEquals(EncodingUtil.FailReason.BY_BOM, result);

    EncodingUtil.saveIn(getProject(), document, null, file, WINDOWS_1251);
    bytes = file.contentsToByteArray();
    assertEquals(WINDOWS_1251, file.getCharset());
    assertNull(file.getBOM());

    Assert.assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, StandardCharsets.UTF_16LE));
    Assert.assertSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, US_ASCII));
    result = EncodingUtil.checkCanReload(file, null);
    assertNull(result);

    EncodingUtil.saveIn(getProject(), document, null, file, StandardCharsets.UTF_16LE);
    bytes = file.contentsToByteArray();
    assertEquals(StandardCharsets.UTF_16LE, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF16LE_BOM, file.getBOM());

    Assert.assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, WINDOWS_1251));
    Assert.assertSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, US_ASCII));
    result = EncodingUtil.checkCanReload(file, null);
    assertNotNull(result);

    text = "xxx";
    setText(document, text);
    Assert.assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, WINDOWS_1251));
    Assert.assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, US_ASCII));

    FileDocumentManager.getInstance().saveAllDocuments();
    bytes = file.contentsToByteArray();
    result = EncodingUtil.checkCanReload(file, null);
    assertNotNull(result);

    text = "qqq";
    setText(document, text);
    Assert.assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, WINDOWS_1251));
    Assert.assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, US_ASCII));

    EncodingUtil.saveIn(getProject(), document, null, file, US_ASCII);
    bytes = file.contentsToByteArray();
    assertEquals(US_ASCII, file.getCharset());
    assertNull(file.getBOM());

    Assert.assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, WINDOWS_1251));
    result = EncodingUtil.checkCanReload(file, null);
    assertNull(result);
  }

  public void testSetEncodingForDirectoryChangesEncodingsForEvenNotLoadedFiles() throws IOException {
    EncodingProjectManager.getInstance(getProject()).setEncoding(null, StandardCharsets.UTF_8);
    UIUtil.dispatchAllInvocationEvents();

    File fc = FileUtil.createTempDirectory("", "");
    VirtualFile root = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fc));
    PsiTestUtil.addContentRoot(getModule(), root);

    VirtualFile file = createChildData(root, "win.txt");
    Files.writeString(file.toNioPath(), THREE_RUSSIAN_LETTERS, WINDOWS_1251);
    file.refresh(false, false);

    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    Assert.assertNull(file.getBOM());


    EncodingProjectManager.getInstance(getProject()).setEncoding(root, WINDOWS_1251);
    UIUtil.dispatchAllInvocationEvents();

    assertEquals(WINDOWS_1251, file.getCharset());
    Assert.assertNull(file.getBOM());
  }

  public void testExternalChangeClearsAutoDetectedFromBytesFlag() throws IOException {
    VirtualFile file = createTempFile("txt", NO_BOM, THREE_RUSSIAN_LETTERS, StandardCharsets.UTF_8);
    getDocument(file);

    assertEquals(LoadTextUtil.AutoDetectionReason.FROM_BYTES, LoadTextUtil.getCharsetAutoDetectionReason(file));

    Files.writeString(file.toNioPath(), THREE_RUSSIAN_LETTERS, WINDOWS_1251);
    file.refresh(false, false);

    Assert.assertNull(LoadTextUtil.getCharsetAutoDetectionReason(file));
  }

  public void testSafeToConvert() throws IOException {
    String text = "xxx";
    VirtualFile file = createTempFile("txt", NO_BOM, text, StandardCharsets.UTF_8);
    byte[] bytes = file.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.ABSOLUTELY, EncodingUtil.isSafeToConvertTo(file, text, bytes, US_ASCII));
    assertEquals(EncodingUtil.Magic8.ABSOLUTELY, EncodingUtil.isSafeToConvertTo(file, text, bytes, WINDOWS_1251));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(file, text, bytes, StandardCharsets.UTF_16BE));

    String rusText = THREE_RUSSIAN_LETTERS;
    VirtualFile rusFile = createTempFile("txt", NO_BOM, rusText, StandardCharsets.UTF_8);
    byte[] rusBytes = rusFile.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(rusFile, rusText, rusBytes, US_ASCII));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(rusFile, rusText, rusBytes, WINDOWS_1251));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(rusFile, rusText, rusBytes,
                                                                                        StandardCharsets.UTF_16BE));

    String bomText = THREE_RUSSIAN_LETTERS;
    VirtualFile bomFile = createTempFile("txt", CharsetToolkit.UTF16LE_BOM, bomText, StandardCharsets.UTF_16LE);
    byte[] bomBytes = bomFile.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(bomFile, bomText, bomBytes, US_ASCII));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(bomFile, bomText, bomBytes, WINDOWS_1251));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(bomFile, bomText, bomBytes,
                                                                                        StandardCharsets.UTF_16BE));
  }
  public void testSafeToReloadUtf8Bom() throws IOException {
    String text = THREE_RUSSIAN_LETTERS;
    VirtualFile file = createTempFile("txt", CharsetToolkit.UTF8_BOM, text, StandardCharsets.UTF_8);
    byte[] bytes = file.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, US_ASCII));
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, StandardCharsets.UTF_16LE));
  }
  public void testSafeToReloadUtf8NoBom() throws IOException {
    String text = THREE_RUSSIAN_LETTERS;
    VirtualFile file = createTempFile("txt", NO_BOM, text, StandardCharsets.UTF_8);
    byte[] bytes = file.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, US_ASCII));
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, StandardCharsets.UTF_16LE));
  }
  public void testSafeToReloadText() throws IOException {
    String text = "xxx";
    VirtualFile file = createTempFile("txt", NO_BOM, text, US_ASCII);
    byte[] bytes = file.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.ABSOLUTELY, EncodingUtil.isSafeToReloadIn(file, text, bytes, WINDOWS_1251));
    assertEquals(EncodingUtil.Magic8.ABSOLUTELY, EncodingUtil.isSafeToReloadIn(file, text, bytes, StandardCharsets.UTF_8));
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, StandardCharsets.UTF_16LE));
  }

  public void testMustBeSafeToReloadISO8859TextMistakenlyLoadedInUTF8() throws IOException {
    @SuppressWarnings("SpellCheckingInspection") String isoText = "No se ha encontrado ningún puesto con ese criterio de búsqueda";
    VirtualFile file = createTempFile("txt", NO_BOM, isoText, StandardCharsets.ISO_8859_1);
    byte[] bytes = isoText.getBytes(StandardCharsets.ISO_8859_1);
    file.setCharset(StandardCharsets.UTF_8);
    String utfText = new String(bytes, StandardCharsets.UTF_8);
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToReloadIn(file, utfText, bytes, StandardCharsets.ISO_8859_1));
  }

  public void testUndoChangeEncoding() throws IOException {
    VirtualFile file = createTempFile("txt", NO_BOM, "xxx", StandardCharsets.UTF_8);
    file.setCharset(StandardCharsets.UTF_8);
    UIUtil.dispatchAllInvocationEvents();
    assertEquals(StandardCharsets.UTF_8, file.getCharset());

    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    assertNull(documentManager.getCachedDocument(file));

    change(file, WINDOWS_1251);
    assertEquals(WINDOWS_1251, file.getCharset());

    Document document = Objects.requireNonNull(documentManager.getDocument(file));
    change(file, StandardCharsets.UTF_8);
    assertEquals(StandardCharsets.UTF_8, file.getCharset());

    globalUndo();
    UIUtil.dispatchAllInvocationEvents();

    assertEquals(WINDOWS_1251, file.getCharset());
    Objects.requireNonNull(document.getText());
  }

  private void globalUndo() {
    UndoManager myManager = UndoManager.getInstance(getProject());
    assertTrue("undo is not available", myManager.isUndoAvailable(null));
    myManager.undo(null);

  }

  private static void change(VirtualFile file, Charset charset) throws IOException {
    new ChangeFileEncodingAction().chosen(getDocument(file), null, file, file.contentsToByteArray(), charset);
  }

  public void testCantReloadBOMDetected() throws IOException {
    VirtualFile file = createTempFile("txt", CharsetToolkit.UTF8_BOM, THREE_RUSSIAN_LETTERS, StandardCharsets.UTF_8);
    file.contentsToByteArray();
    Document document = Objects.requireNonNull(FileDocumentManager.getInstance().getDocument(file));
    assertEquals(THREE_RUSSIAN_LETTERS, document.getText());
    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    EncodingProjectManager.getInstance(getProject()).setEncoding(file, StandardCharsets.UTF_16LE);
    UIUtil.dispatchAllInvocationEvents();

    assertEquals(StandardCharsets.UTF_8, file.getCharset());
  }

  public void testExternalChangeStrippedBOM() throws IOException {
    String text = "text";
    VirtualFile file = createTempFile("txt", CharsetToolkit.UTF8_BOM, text, StandardCharsets.UTF_8);
    file.contentsToByteArray();
    Document document = Objects.requireNonNull(FileDocumentManager.getInstance().getDocument(file));

    assertEquals(text, document.getText());
    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF8_BOM, file.getBOM());

    FileUtil.writeToFile(VfsUtilCore.virtualToIoFile(file), text.getBytes(StandardCharsets.UTF_8));
    file.refresh(false, false);
    UIUtil.dispatchAllInvocationEvents();

    assertEquals(text, document.getText());
    assertEquals(defaultProjectEncoding(), file.getCharset());
    assertNull(file.getBOM());
  }

  public void testNewFileCreatedInProjectEncoding() throws IOException {
    EditorTestUtil.saveEncodingsIn(getProject(), StandardCharsets.UTF_8, WINDOWS_1251, () -> {
      PsiFile psiFile = createFile("x.txt", "xx");
      VirtualFile file = psiFile.getVirtualFile();

      assertEquals(WINDOWS_1251, file.getCharset());
    });
  }

  public void testNewFileCreatedInProjectEncodingEvenIfItSetToDefault() throws IOException {
    EditorTestUtil.saveEncodingsIn(getProject(), Charset.defaultCharset().name().equals("UTF-8") ? WINDOWS_1251 : StandardCharsets.UTF_8, null, () -> {
      PsiFile psiFile = createFile("x.txt", "xx");
      VirtualFile file = psiFile.getVirtualFile();

      assertEquals(EncodingProjectManager.getInstance(getProject()).getDefaultCharset(), file.getCharset());
    });
  }

  private @NotNull PsiFile createFile(@NotNull String fileName, @NotNull String text) throws IOException {
    File dir = createTempDirectory();
    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));

    assert vDir != null : dir;
    return WriteAction.compute(() -> {
      if (!ModuleRootManager.getInstance(getModule()).getFileIndex().isInSourceContent(vDir)) {
        PsiTestUtil.addSourceContentToRoots(getModule(), vDir);
      }

      VirtualFile vFile = vDir.createChildData(vDir, fileName);
      LoadTextUtil.write(getProject(), vFile, this, text, -1);
      assertNotNull(vFile);
      PsiFile file = getPsiManager().findFile(vFile);
      assertNotNull(file);
      return file;
    });
  }

  public void testTheDefaultProjectEncodingIfNotSpecifiedShouldBeIDEEncoding() throws Exception {
    Charset differentFromDefault = Charset.defaultCharset().equals(WINDOWS_1252) ? WINDOWS_1251 : WINDOWS_1252;
    String oldIDE = EncodingManager.getInstance().getDefaultCharsetName();
    try {
      EncodingManager.getInstance().setDefaultCharsetName(differentFromDefault.name());

      File temp = createTempDirectory();
      VirtualFile tempDir = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(temp));

      Project newProject = ProjectManagerEx.getInstanceEx().newProject(Paths.get(tempDir.getPath()), new OpenProjectTaskBuilder().build());
      PlatformTestUtil.openProject(newProject);
      try {
        PlatformTestUtil.saveProject(newProject);

        Charset newProjectEncoding = EncodingProjectManager.getInstance(newProject).getDefaultCharset();
        assertEquals(differentFromDefault, newProjectEncoding);

        VirtualFile vFile = WriteCommandAction.runWriteCommandAction(newProject, (ThrowableComputable<VirtualFile, IOException>)() -> {
          Module newModule = ModuleManager.getInstance(newProject).newModule(tempDir.getPath(), "blah");
          ModuleRootModificationUtil.addContentRoot(newModule, tempDir);
          VirtualFile v = tempDir.createChildData(this, "x.txt");
          LoadTextUtil.write(null, v, this, "xx", -1);
          return v;
        });
        assertEquals(newProject, ProjectLocator.getInstance().guessProjectForFile(vFile));

        assertEquals(newProjectEncoding, vFile.getCharset());
      }
      finally {
        PlatformTestUtil.forceCloseProjectWithoutSaving(newProject);
      }
    }
    finally {
      EncodingManager.getInstance().setDefaultCharsetName(oldIDE);
    }
  }

  public void testFileMustNotLoadInWrongEncodingIfAccessedBeforeProjectOpen() {
    VirtualFile dir = find("newEncoding");
    VirtualFile file = Objects.requireNonNull(dir.findFileByRelativePath("src/xxx.txt"));

    Document document = Objects.requireNonNull(FileDocumentManager.getInstance().getDocument(file));
    assertNotNull(document.getText());
    UIUtil.dispatchAllInvocationEvents();

    Project newEncodingProject = PlatformTestUtil.loadAndOpenProject(dir.toNioPath(), getTestRootDisposable());
    assertEquals(US_ASCII, file.getCharset());
    assertTrue(newEncodingProject.isOpen());
  }

  public void testFileInsideJarCorrectlyHandlesBOM() throws IOException {
    File tmpDir = createTempDirectory();
    File jar = new File(tmpDir, "x.jar");
    String text = "update";
    byte[] bytes = ArrayUtil.mergeArrays(CharsetToolkit.UTF16BE_BOM, text.getBytes(StandardCharsets.UTF_16BE));
    String name = "some_random_name";
    IoTestUtil.createTestJar(jar, Collections.singletonList(Pair.create(name, bytes)));
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
    assertNotNull(vFile);
    VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(vFile);
    assertNotNull(jarRoot);

    VirtualFile file = jarRoot.findChild(name);
    assertNotNull(file);
    assertEquals(StandardCharsets.UTF_16BE, file.getCharset());
    assertEquals(PlainTextFileType.INSTANCE, file.getFileType());
    assertEquals(text, LoadTextUtil.loadText(file).toString());
    try (InputStream stream = file.getInputStream()) {
      String loaded = new String(FileUtil.loadBytes(stream), StandardCharsets.UTF_16BE);
      assertEquals(text, loaded);
    }
  }

  public void testBigFileInsideJarCorrectlyHandlesBOM() throws IOException {
    File tmpDir = createTempDirectory();
    File jar = new File(tmpDir, "x.jar");
    String bigText = StringUtil.repeat("u", FileUtilRt.LARGE_FOR_CONTENT_LOADING+1);
    byte[] utf16beBytes = ArrayUtil.mergeArrays(CharsetToolkit.UTF16BE_BOM, bigText.getBytes(StandardCharsets.UTF_16BE));
    String name = "some_random_name";
    IoTestUtil.createTestJar(jar, Collections.singletonList(Pair.create(name, utf16beBytes)));
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
    assertNotNull(vFile);
    VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(vFile);
    assertNotNull(jarRoot);

    VirtualFile file = jarRoot.findChild(name);
    assertNotNull(file);
    try (InputStream stream = file.getInputStream()) {
      String loaded = new String(FileUtil.loadBytes(stream, 8192*2), StandardCharsets.UTF_16BE);
      assertEquals(bigText.substring(0, 8192), loaded);
    }
    assertEquals(PlainTextFileType.INSTANCE, file.getFileType());
    assertEquals(StandardCharsets.UTF_16BE, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF16BE_BOM, file.getBOM());
    // should not load the entire file anyway
    //assertEquals(text, LoadTextUtil.loadText(file).toString());
  }

  public void testSevenBitFileTextOptimisationWorks() throws IOException {
    PsiFile file = createFile("x.txt", "some random\nfile content\n");
    VirtualFile virtualFile = file.getVirtualFile();
    CharSequence loaded = LoadTextUtil.loadText(virtualFile);
    assertInstanceOf(loaded, ByteArrayCharSequence.class);
  }

  public void testUTF16LEWithNoBOMIsAThing() throws IOException {
    String text = "text";
    VirtualFile vFile = createTempFile("txt", NO_BOM, text, StandardCharsets.UTF_16LE);

    vFile.setCharset(StandardCharsets.UTF_16LE);
    vFile.setBOM(null);
    CharSequence loaded = LoadTextUtil.loadText(vFile);
    assertEquals(text, loaded.toString());
  }

  public void testNewUTF8FileCanBeCreatedWithOrWithoutBOMDependingOnTheSettings() throws IOException {
    EncodingProjectManagerImpl manager = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject());
    EncodingProjectManagerImpl.BOMForNewUTF8Files old = manager.getBOMForNewUTF8Files();
    String oldProject = manager.getDefaultCharsetName();
    manager.setDefaultCharsetName(CharsetToolkit.UTF8);
    try {
      manager.setBOMForNewUtf8Files(EncodingProjectManagerImpl.BOMForNewUTF8Files.NEVER);
      VirtualFile file = createFile("x.txt", "xx").getVirtualFile();
      assertNull(file.getBOM());
      @org.intellij.lang.annotations.Language("XML")
      String xxTag = "<xx/>";
      // internal files must never be BOMed
      VirtualFile imlFile = createFile("x.iml", xxTag).getVirtualFile();
      assertNull(imlFile.getBOM());

      manager.setBOMForNewUtf8Files(EncodingProjectManagerImpl.BOMForNewUTF8Files.ALWAYS);
      VirtualFile file2 = createFile("x2.txt", "xx").getVirtualFile();
      assertArrayEquals(CharsetToolkit.UTF8_BOM, file2.getBOM());
      // internal files must never be BOMed
      imlFile = createFile("x2.iml", xxTag).getVirtualFile();
      assertNull(imlFile.getBOM());

      manager.setBOMForNewUtf8Files(EncodingProjectManagerImpl.BOMForNewUTF8Files.WINDOWS_ONLY);
      VirtualFile file3 = createFile("x3.txt", "xx").getVirtualFile();
      byte[] expected = SystemInfo.isWindows ? CharsetToolkit.UTF8_BOM : null;
      assertArrayEquals(expected, file3.getBOM());
      // internal files must never be BOMed
      imlFile = createFile("x3.iml", xxTag).getVirtualFile();
      assertNull(imlFile.getBOM());

      manager.setBOMForNewUtf8Files(EncodingProjectManagerImpl.BOMForNewUTF8Files.NEVER);
      VirtualFile file4 = createFile("x4.txt", "xx").getVirtualFile();
      assertNull(file4.getBOM());
      // internal files must never be BOMed
      imlFile = createFile("x4.iml", xxTag).getVirtualFile();
      assertNull(imlFile.getBOM());
    }
    finally {
      manager.setBOMForNewUtf8Files(old);
      manager.setDefaultCharsetName(oldProject);
    }
  }

  public void testBigFileAutoDetectedAsTextMustDetermineItsEncodingFromTheWholeTextToMinimizePossibilityOfUmlautInTheEndMisDetectionError() throws IOException {
    // must not allow sheer luck to have guessed UTF-8 correctly
    EncodingProjectManager.getInstance(getProject()).setDefaultCharsetName(US_ASCII.name());
    VirtualFile src = getTestRoot().findChild("BIG_CHANGES");
    assertNotNull(src);

    // create new file to avoid caching the file AUTO_DETECTED attribute. we need to re-detect it from scratch to test its correctness
    VirtualFile file = createTempFile("blah-blah", NO_BOM, new String(src.contentsToByteArray(), StandardCharsets.UTF_8), StandardCharsets.UTF_8);

    assertNull(file.getBOM());
    assertEquals(PlainTextFileType.INSTANCE, file.getFileType());
    assertEquals(StandardCharsets.UTF_8, file.getCharset());
  }

  public void testEncodingReDetectionRequestsOnDocumentChangeAreBatchedToImprovePerformance() throws IOException {
    VirtualFile file = createTempFile("txt", NO_BOM, "xxx", US_ASCII);
    Document document = Objects.requireNonNull(getDocument(file));
    WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(0, " "));
    EncodingManagerImpl encodingManager = (EncodingManagerImpl)EncodingManager.getInstance();
    encodingManager.waitAllTasksExecuted(60, TimeUnit.SECONDS);
    PlatformTestUtil.startPerformanceTest("encoding re-detect requests", 10_000, ()->{
      for (int i=0; i<100_000_000;i++) {
        encodingManager.queueUpdateEncodingFromContent(document);
      }
      encodingManager.waitAllTasksExecuted(60, TimeUnit.SECONDS);
      UIUtil.dispatchAllInvocationEvents();
    }).assertTiming();
  }

  public void testEncodingDetectionRequestsRunInOneThreadForEachDocument() throws IOException {
    Set<Thread> detectThreads = ContainerUtil.newConcurrentSet();
    class MyFT extends LanguageFileType implements FileTypeIdentifiableByVirtualFile {
      private MyFT() { super(new Language("my") {}); }
      @Override public boolean isMyFileType(@NotNull VirtualFile file) { return getDefaultExtension().equals(file.getExtension()); }
      @Override public @NotNull String getName() { return "my"; }
      @Override public @NotNull String getDescription() { return getName(); }
      @Override public @NotNull String getDefaultExtension() { return "my"; }
      @Override public Icon getIcon() { return null; }

      @Override
      public Charset extractCharsetFromFileContent(@Nullable Project project, @Nullable VirtualFile file, @NotNull CharSequence content) {
        detectThreads.add(Thread.currentThread());
        TimeoutUtil.sleep(1000);
        return US_ASCII;
      }
    }

    FileType foo = new MyFT();
    ((FileTypeManagerImpl)FileTypeManagerEx.getInstanceEx()).registerFileType(foo, List.of(), getTestRootDisposable());

    VirtualFile file = createTempFile("my", NO_BOM, StringUtil.repeat("c", 20), US_ASCII);
    FileEditorManager.getInstance(getProject()).openFile(file, false);

    Document document = getDocument(file);
    assertEquals(foo, file.getFileType());
    file.setCharset(null);
    detectThreads.clear();

    for (int i=0; i<1000; i++) {
      WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, " "));
    }

    EncodingManagerImpl encodingManager = (EncodingManagerImpl)EncodingManager.getInstance();
    encodingManager.waitAllTasksExecuted(60, TimeUnit.SECONDS);
    UIUtil.dispatchAllInvocationEvents();

    Thread thread = assertOneElement(detectThreads);
    ApplicationManager.getApplication().assertIsDispatchThread();
    assertNotEquals(Thread.currentThread(), thread);
  }

  public void testSetMappingMustResetEncodingOfNotYetLoadedFiles() {
    VirtualFile dir = getTempDir().createVirtualDir();
    ModuleRootModificationUtil.addContentRoot(getModule(), dir);
    VirtualFile file = createChildData(dir, "my.txt");
    setFileText(file, "xxx");
    file.setCharset(US_ASCII);
    FileDocumentManager.getInstance().saveAllDocuments();
    UIUtil.dispatchAllInvocationEvents();

    assertNull(FileDocumentManager.getInstance().getCachedDocument(file));
    assertEquals(US_ASCII, file.getCharset());

    ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject())).setMapping(Collections.singletonMap(dir, WINDOWS_1251));
    assertEquals(WINDOWS_1251, file.getCharset());
  }

  public void testEncodingMappingMustNotContainInvalidFiles() {
    VirtualFile dir = getTempDir().createVirtualDir();
    VirtualFile root = dir.getParent();
    ModuleRootModificationUtil.addContentRoot(getModule(), dir);
    FileDocumentManager.getInstance().saveAllDocuments();
    UIUtil.dispatchAllInvocationEvents();

    ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject())).setMapping(Collections.singletonMap(dir, WINDOWS_1251));
    Set<? extends VirtualFile> mappings = ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject())).getAllMappings().keySet();
    assertTrue(mappings.contains(dir));

    delete(dir);
    UIUtil.dispatchAllInvocationEvents();
    assertFalse(dir.isValid());

    mappings = ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject())).getAllMappings().keySet();
    assertFalse(mappings.contains(dir));
    for (VirtualFile mapping : mappings) {
      assertTrue(mapping.isValid());
    }

    dir = createChildDirectory(root, dir.getName());

    mappings = ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject())).getAllMappings().keySet();
    assertTrue(mappings.contains(dir));
    for (VirtualFile mapping : ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject())).getAllMappings().keySet()) {
      assertTrue(mapping.isValid());
    }
  }

  public void testFileEncodingProviderOverridesMapping() throws IOException {
    FileEncodingProvider encodingProvider = new FileEncodingProvider() {
      @Override
      public Charset getEncoding(@NotNull VirtualFile virtualFile) {
        return StandardCharsets.UTF_16;
      }
    };
    FileEncodingProvider.EP_NAME.getPoint().registerExtension(encodingProvider, getTestRootDisposable());
    VirtualFile file = createTempFile("txt", NO_BOM, "some", StandardCharsets.UTF_8);
    EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(getProject());
    encodingManager.setEncoding(file, StandardCharsets.ISO_8859_1);
    try {
      Charset charset = encodingManager.getEncoding(file, true);
      assertEquals(StandardCharsets.UTF_16, charset);
    }
    finally {
      encodingManager.setEncoding(file, null);
    }
  }

  public void testForcedCharsetOverridesFileEncodingProvider() throws IOException {
    final String ext = "yyy";
    class MyForcedFileType extends LanguageFileType {
      protected MyForcedFileType() { super(new Language("test") {}); }
      @Override public @NotNull String getName() { return "Test"; }
      @Override public @NotNull String getDescription() { return "Test"; }
      @Override public @NotNull String getDefaultExtension() { return ext; }
      @Override public Icon getIcon() { return null; }
      @Override public String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) { return StandardCharsets.ISO_8859_1.name(); }
    }
    MyForcedFileType fileType = new MyForcedFileType();
    FileEncodingProvider encodingProvider = __ -> StandardCharsets.UTF_16;
    FileEncodingProvider.EP_NAME.getPoint().registerExtension(encodingProvider, getTestRootDisposable());
    FileTypeManagerImpl fileTypeManager = (FileTypeManagerImpl)FileTypeManagerEx.getInstanceEx();
    fileTypeManager.registerFileType(fileType, List.of(new ExtensionFileNameMatcher(ext)), getTestRootDisposable());
    VirtualFile file = createTempFile(ext, NO_BOM, "some", StandardCharsets.UTF_8);
    assertEquals(fileType, file.getFileType());
    assertEquals(StandardCharsets.ISO_8859_1, file.getCharset());
  }

  public void testDetectedCharsetOverridesFileEncodingProvider() throws IOException {
    FileEncodingProvider encodingProvider = __ -> WINDOWS_1251;
    FileEncodingProvider.EP_NAME.getPoint().registerExtension(encodingProvider, getTestRootDisposable());
    VirtualFile file = createTempFile("yyy", NO_BOM, "Some text" + THREE_RUSSIAN_LETTERS, StandardCharsets.UTF_8);
    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    assertEquals(LoadTextUtil.AutoDetectionReason.FROM_BYTES, LoadTextUtil.getCharsetAutoDetectionReason(file));
  }

  private @NotNull VirtualFile createTempFile(@NotNull String ext, byte @NotNull [] BOM, @NotNull String content, @NotNull Charset charset) throws IOException {
    File file = FileUtil.createTempFile("copy", "." + ext);
    FileOutputStream stream = new FileOutputStream(file);
    stream.write(BOM);
    try (OutputStreamWriter writer = new OutputStreamWriter(stream, charset)) {
      writer.write(content);
    }

    disposeOnTearDown(() -> FileUtil.delete(file));
    return getVirtualFile(file);
  }

  public void testEncodingWidgetMustBeAvailableForReadonlyFiles() {
    EncodingPanel panel = new EncodingPanel(getProject()){
      @Override
      protected VirtualFile getSelectedFile() {
        LightVirtualFile file = new LightVirtualFile("x.txt", "xxx");
        file.setWritable(false);
        return file;
      }
    };
    panel.updateInTests(true);
    assertTrue(panel.isActionEnabled());
  }
}
