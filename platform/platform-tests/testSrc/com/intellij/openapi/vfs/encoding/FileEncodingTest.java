/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.encoding;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.copy.CopyFilesOrDirectoriesHandler;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.text.ByteArrayCharSequence;
import com.intellij.util.text.XmlCharsetDetector;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;

@SuppressWarnings("HardCodedStringLiteral")
public class FileEncodingTest extends PlatformTestCase implements TestDialog {
  private static final Charset US_ASCII = CharsetToolkit.US_ASCII_CHARSET;
  private static final Charset WINDOWS_1251 = CharsetToolkit.WIN_1251_CHARSET;
  private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
  private static final String UTF8_XML_PROLOG = prolog(CharsetToolkit.UTF8_CHARSET);
  private static final byte[] NO_BOM = ArrayUtil.EMPTY_BYTE_ARRAY;
  private static final String XML_TEST_BODY = "<web-app>\n" + "<!--\u043f\u0430\u043f\u0430-->\n" + "</web-app>";
  private static final String THREE_RUSSIAN_LETTERS = "\u0416\u041e\u041f";
  private TestDialog myOldTestDialogValue;

  private static String prolog(Charset charset) {
    return "<?xml version=\"1.0\" encoding=\"" + charset.name() + "\"?>\n";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myOldTestDialogValue = Messages.setTestDialog(this);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Messages.setTestDialog(myOldTestDialogValue);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  public int show(String message) {
    return 0;
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
    VirtualFile xml = ObjectUtils.assertNotNull(vTestRoot.findChild("xNotepadUtf8.xml"));

    String expected = UTF8_XML_PROLOG + XML_TEST_BODY;
    String text = getDocument(xml).getText();

    if (!expected.equals(text)) {
      System.err.print("expected = ");
      for (int i=0; i<50;i++) {
        final char c = expected.charAt(i);
        System.err.print(Integer.toHexString((int)c)+", ");
      }
      System.err.println();
      System.err.print("expected bytes = ");
      byte[] expectedBytes = FileUtil.loadFileBytes(new File(xml.getPath()));
      for (int i=0; i<50;i++) {
        final byte c = expectedBytes[i];
        System.err.print(Integer.toHexString((int)c) + ", ");
      }
      System.err.println();

      System.err.print("text = ");
      for (int i=0; i<50;i++) {
        final char c = text.charAt(i);
        System.err.print(Integer.toHexString((int)c)+", ");
      }
      System.err.println();
      System.err.print("text bytes = ");
      byte[] textBytes = xml.contentsToByteArray();
      for (int i=0; i<50;i++) {
        final byte c = textBytes[i];
        System.err.print(Integer.toHexString((int)c) + ", ");
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
    final File dir = createTempDirectory();
    final File file = new File(dir, "copy.xml");
    FileUtil.copy(new File(src.getPath()), file);

    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        VirtualFile xml = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        Document document = getDocument(xml);

        setText(document, UTF8_XML_PROLOG + XML_TEST_BODY);
        FileDocumentManager.getInstance().saveAllDocuments();

        byte[] savedBytes = FileUtil.loadFileBytes(file);
        String saved = new String(savedBytes, CharsetToolkit.UTF8).replace("\r\n", "\n");
        String expected = (UTF8_XML_PROLOG + XML_TEST_BODY).replace("\r\n", "\n");

        assertEquals(expected, saved);
      }
    }.execute().throwException();
  }

  public void testDefaultHtml() {
    VirtualFile file = find("defaultHtml.html");

    assertEquals(EncodingProjectManager.getInstance(getProject()).getDefaultCharset(), file.getCharset());
  }

  @NotNull
  private static VirtualFile find(String name) {
    return ObjectUtils.assertNotNull(getTestRoot().findChild(name));
  }

  public void testTrickyProlog() {
    VirtualFile xml = find("xTrickyProlog.xml");

    assertEquals("Big5", xml.getCharset().name());
  }

  public void testDefaultXml() {
    VirtualFile xml = find("xDefault.xml");

    assertEquals(CharsetToolkit.UTF8_CHARSET, xml.getCharset());
  }

  public void testIbm866() {
    VirtualFile xml = find("xIbm866.xml");

    String expected = prolog(Charset.forName("IBM866")) + XML_TEST_BODY;
    String text = getDocument(xml).getText();

    assertEquals(expected, text);
  }

  private static VirtualFile getTestRoot() {
    final File testRoot = new File(PathManagerEx.getCommunityHomePath(), "platform/platform-tests/testData/vfs/encoding");
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

    File copy = FileUtil.createTempFile("copy", ".txt");
    myFilesToDelete.add(copy);
    FileUtil.copy(source, copy);
    VirtualFile fileCopy = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(copy);
    document = getDocument(fileCopy);
    //assertTrue(CharsetToolkit.hasUTF16LEBom(fileCopy.getBOM()));

    setText(document, "\u04ab\u04cd\u04ef");
    FileDocumentManager.getInstance().saveAllDocuments();
    byte[] bytes = FileUtil.loadFileBytes(copy);
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

  private static void doHtmlTest(final String metaWithWindowsEncoding, final String metaWithUtf8Encoding) throws IOException {
    File temp = FileUtil.createTempFile("copy", ".html");
    setContentOnDisk(temp, NO_BOM,
                     "<html><head>" + metaWithWindowsEncoding + "</head>" +
                     THREE_RUSSIAN_LETTERS +
                     "</html>",
                     WINDOWS_1252);

    myFilesToDelete.add(temp);
    VirtualFile file = ObjectUtils.assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(temp));

    assertEquals(WINDOWS_1252, file.getCharset());

    Document document = getDocument(file);
    setText(document, "<html><head>" + metaWithUtf8Encoding + "</head>" +
                      THREE_RUSSIAN_LETTERS +
                      "</html>");
    FileDocumentManager.getInstance().saveAllDocuments();

    assertEquals(CharsetToolkit.UTF8_CHARSET, file.getCharset());
  }

  public void testHtmlEncodingPreferBOM() throws IOException {
    VirtualFile file = createTempFile("html", CharsetToolkit.UTF8_BOM,
                                      "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1252\"></head>" +
                                      THREE_RUSSIAN_LETTERS +
                                      "</html>",
                                      WINDOWS_1252);

    assertEquals(CharsetToolkit.UTF8_CHARSET, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF8_BOM, file.getBOM());
  }

  public void testHtmlEncodingCaseInsensitive() throws IOException {
    VirtualFile file = createTempFile("html", NO_BOM, "<html>\n" +
                                                      "<head>\n" +
                                                      "<meta http-equiv=\"content-type\" content=\"text/html;charset=us-ascii\"> \n" +
                                                      "</head>\n" +
                                                      "<body>\n" +
                                                      "xyz\n" +
                                                      "</body>\n" +
                                                      "</html>",
                                      WINDOWS_1252);

    assertEquals(US_ASCII, file.getCharset());
  }
  public void testHtmlContentAttributeOrder() throws IOException {
    VirtualFile file =
      createTempFile("html", NO_BOM, "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"\n" +
                                     "\t\t\"http://www.w3.org/TR/html4/loose.dtd\">\n" +
                                     "<html> <head>\n" +
                                     "\t<meta content=\"text/html;charset=US-ASCII\" http-equiv=\"Content-Type\">\n" +
                                     "</head> </html>",
                     WINDOWS_1252);

    assertEquals(US_ASCII, file.getCharset());
  }

  private static void setText(final Document document, final String text) {
    ApplicationManager.getApplication().runWriteAction(() -> document.setText(text));
  }

  public void testXHtmlStuff() throws IOException {
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
    final StringBuilder text = new StringBuilder(THREE_RUSSIAN_LETTERS);
    VirtualFile file = createTempFile("txt", NO_BOM, text.toString(), WINDOWS_1251);
    File ioFile = new File(file.getPath());

    EncodingManager.getInstance().setEncoding(file, WINDOWS_1251);

    final Document document = getDocument(file);
    final boolean[] changed = {false};
    document.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(final DocumentEvent event) {
        changed[0] = true;
      }
    });

    EncodingManager.getInstance().setEncoding(file, CharsetToolkit.UTF8_CHARSET);
    //text in editor changed
    assertEquals(CharsetToolkit.UTF8_CHARSET, file.getCharset());
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(changed[0]);
    changed[0] = false;

    FileDocumentManager.getInstance().saveAllDocuments();
    byte[] bytes = FileUtil.loadFileBytes(ioFile);
    //file on disk is still windows
    assertTrue(Arrays.equals(text.toString().getBytes(WINDOWS_1251.name()), bytes));

    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(getProject(), () -> {
      document.insertString(0, "x");
      text.insert(0, "x");
    }, null, null));

    assertTrue(changed[0]);
    changed[0] = false;
    EncodingManager.getInstance().setEncoding(file, US_ASCII);
    assertEquals(US_ASCII, file.getCharset());
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(changed[0]); //reloaded again

    //after 'save as us' file on disk changed
    bytes = FileUtil.loadFileBytes(ioFile);
    assertTrue(Arrays.equals(text.toString().getBytes(US_ASCII.name()), bytes));
  }

  public void testCopyMove() throws IOException {
    File root = createTempDirectory();
    File dir1 = new File(root, "dir1");
    assertTrue(dir1.mkdir());
    File dir2 = new File(root, "dir2");
    assertTrue(dir2.mkdir());
    final VirtualFile vdir1 = getVirtualFile(dir1);
    final VirtualFile vdir2 = getVirtualFile(dir2);
    assertNotNull(dir1.getPath(), vdir1);
    assertNotNull(dir2.getPath(), vdir2);

    EncodingManager.getInstance().setEncoding(vdir1, WINDOWS_1251);
    EncodingManager.getInstance().setEncoding(vdir2, US_ASCII);
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        VirtualFile xxx = vdir1.createChildData(this, "xxx.txt");
        setFileText(xxx, THREE_RUSSIAN_LETTERS);
        assertEquals(WINDOWS_1251, xxx.getCharset());
        VirtualFile copied = xxx.copy(this, vdir2, "xxx2.txt");
        assertEquals(WINDOWS_1251, copied.getCharset());

        VirtualFile xus = vdir2.createChildData(this, "xxxus.txt");
        setFileText(xus, THREE_RUSSIAN_LETTERS);
        assertEquals(US_ASCII, xus.getCharset());

        xus.move(this, vdir1);
        assertEquals(US_ASCII, xus.getCharset());
      }
    }.execute().throwException();
  }

  public void testCopyNested() throws IOException {
    File root = createTempDirectory();
    File dir1 = new File(root, "dir1");
    assertTrue(dir1.mkdir());
    File dir2 = new File(root, "dir2");
    assertTrue(dir2.mkdir());
    final VirtualFile vdir1 = getVirtualFile(dir1);
    final VirtualFile vdir2 = getVirtualFile(dir2);
    assertNotNull(dir1.getPath(), vdir1);
    assertNotNull(dir2.getPath(), vdir2);

    EncodingManager.getInstance().setEncoding(vdir1, WINDOWS_1251);
    EncodingManager.getInstance().setEncoding(vdir2, US_ASCII);
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        VirtualFile winf = vdir1.createChildData(this, "xxx.txt");

        PsiDirectory psidir1 = ObjectUtils.assertNotNull(PsiManager.getInstance(getProject()).findDirectory(vdir1));
        PsiDirectory psidir2 = ObjectUtils.assertNotNull(PsiManager.getInstance(getProject()).findDirectory(vdir2));
        CopyFilesOrDirectoriesHandler.copyToDirectory(psidir1, psidir1.getName(), psidir2);
        VirtualFile winfCopy = ObjectUtils.assertNotNull(psidir2.getVirtualFile().findFileByRelativePath(psidir1.getName() + "/" + winf.getName()));
        assertEquals(WINDOWS_1251, winfCopy.getCharset());
        VirtualFile dir1Copy = psidir2.getVirtualFile().findChild(psidir1.getName());
        assertEquals(WINDOWS_1251, EncodingManager.getInstance().getEncoding(dir1Copy, false));
        assertNull(EncodingManager.getInstance().getEncoding(winfCopy, false));
      }
    }.execute().throwException();
  }

  public void testBOMPersistsAfterEditing() throws IOException {
    VirtualFile file = createTempFile("txt", CharsetToolkit.UTF8_BOM, XML_TEST_BODY, CharsetToolkit.UTF8_CHARSET);

    assertEquals(CharsetToolkit.UTF8_CHARSET, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF8_BOM, file.getBOM());

    Document document = getDocument(file);
    setText(document, "hren");
    FileDocumentManager.getInstance().saveAllDocuments();

    assertEquals(CharsetToolkit.UTF8_CHARSET, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF8_BOM, file.getBOM());

    byte[] bytes = FileUtil.loadFileBytes(VfsUtilCore.virtualToIoFile(file));
    assertTrue(CharsetToolkit.hasUTF8Bom(bytes));
  }

  public void testBOMPersistsAfterIndexRescan() throws IOException {
    VirtualFile file = createTempFile("txt", CharsetToolkit.UTF8_BOM, XML_TEST_BODY, CharsetToolkit.UTF8_CHARSET);

    assertEquals(CharsetToolkit.UTF8_CHARSET, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF8_BOM, file.getBOM());

    Document document = getDocument(file);
    String newContent = "hren";
    setText(document, newContent);

    FileDocumentManager.getInstance().saveAllDocuments();

    byte[] bytes = FileUtil.loadFileBytes(VfsUtilCore.virtualToIoFile(file));
    Charset charset = LoadTextUtil.detectCharsetAndSetBOM(file, bytes, file.getFileType());
    assertEquals(CharsetToolkit.UTF8_CHARSET, charset);
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
    VirtualFile file = find("UTF16LE_NOBOM.txt");
    file.setCharset(CharsetToolkit.UTF_16LE_CHARSET);
    file.setBOM(null);
    String vfsLoad = VfsUtilCore.loadText(file);
    assertEquals("\u041f\u0440\u0438\u0432\u0435\u0442", vfsLoad);
    assertNull(file.getBOM());
  }

  public void testBOMResetAfterChangingUtf16ToUtf8() throws IOException {
    VirtualFile file = createTempFile("txt", CharsetToolkit.UTF16BE_BOM, THREE_RUSSIAN_LETTERS,
                                      CharsetToolkit.UTF_16BE_CHARSET);

    assertEquals(CharsetToolkit.UTF_16BE_CHARSET, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF16BE_BOM, file.getBOM());

    Document document = getDocument(file);
    String newContent = "hren";
    setText(document, newContent);
    FileDocumentManager.getInstance().saveAllDocuments();
    assertEquals(CharsetToolkit.UTF_16BE_CHARSET, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF16BE_BOM, file.getBOM());

    EncodingUtil.saveIn(document, null, file, CharsetToolkit.UTF8_CHARSET);

    byte[] bytes = FileUtil.loadFileBytes(VfsUtilCore.virtualToIoFile(file));

    assertEquals(CharsetToolkit.UTF8_CHARSET, file.getCharset());
    assertNull(file.getBOM());

    assertFalse(CharsetToolkit.hasUTF8Bom(bytes));
  }

  public void testConvertNotAvailableForHtml() throws IOException {
    VirtualFile file = createTempFile("html", null,
                                      "<html><head><meta content=\"text/html; charset=utf-8\" http-equiv=\"content-type\"></head>" +
                                      "<body>" + THREE_RUSSIAN_LETTERS +
                                      "</body></html>",
                                      CharsetToolkit.UTF8_CHARSET);
    Document document = FileDocumentManager.getInstance().getDocument(file);
    assertNotNull(document);
    FileDocumentManager.getInstance().saveAllDocuments();
    EncodingUtil.FailReason result = EncodingUtil.checkCanConvert(file);
    assertNotNull(result);
  }

  public void testConvertReload() throws IOException {
    VirtualFile file = createTempFile("txt", CharsetToolkit.UTF16BE_BOM, THREE_RUSSIAN_LETTERS, CharsetToolkit.UTF_16BE_CHARSET);

    assertEquals(CharsetToolkit.UTF_16BE_CHARSET, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF16BE_BOM, file.getBOM());

    Document document = getDocument(file);

    byte[] bytes = file.contentsToByteArray();
    String text = document.getText();
    Assert.assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, WINDOWS_1251));
    Assert.assertSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, US_ASCII));
    EncodingUtil.FailReason result = EncodingUtil.checkCanReload(file, null);
    assertNotNull(result);

    EncodingUtil.saveIn(document, null, file, WINDOWS_1251);
    bytes = file.contentsToByteArray();
    assertEquals(WINDOWS_1251, file.getCharset());
    assertNull(file.getBOM());

    Assert.assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, CharsetToolkit.UTF_16LE_CHARSET));
    Assert.assertSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, US_ASCII));
    result = EncodingUtil.checkCanReload(file, null);
    assertNull(result);

    EncodingUtil.saveIn(document, null, file, CharsetToolkit.UTF_16LE_CHARSET);
    bytes = file.contentsToByteArray();
    assertEquals(CharsetToolkit.UTF_16LE_CHARSET, file.getCharset());
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

    EncodingUtil.saveIn(document, null, file, US_ASCII);
    bytes = file.contentsToByteArray();
    assertEquals(US_ASCII, file.getCharset());
    assertNull(file.getBOM());

    Assert.assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, WINDOWS_1251));
    result = EncodingUtil.checkCanReload(file, null);
    assertNull(result);
  }

  public void testSetEncodingForDirectoryChangesEncodingsForEvenNotLoadedFiles() throws IOException {
    EncodingProjectManager.getInstance(getProject()).setEncoding(null, CharsetToolkit.UTF8_CHARSET);
    File fc = FileUtil.createTempDirectory("", "");
    VirtualFile root = ObjectUtils.assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fc));
    PsiTestUtil.addContentRoot(getModule(), root);

    VirtualFile file = createChildData(root, "win.txt");
    setContentOnDisk(new File(file.getPath()), null, THREE_RUSSIAN_LETTERS, WINDOWS_1251);
    file.refresh(false, false);

    assertEquals(CharsetToolkit.UTF8_CHARSET, file.getCharset());
    Assert.assertNull(file.getBOM());


    EncodingProjectManager.getInstance(getProject()).setEncoding(root, WINDOWS_1251);
    UIUtil.dispatchAllInvocationEvents();

    assertEquals(WINDOWS_1251, file.getCharset());
    Assert.assertNull(file.getBOM());
  }

  public void testExternalChangeClearsAutoDetectedFromBytesFlag() throws IOException {
    VirtualFile file = createTempFile("txt", null, THREE_RUSSIAN_LETTERS, CharsetToolkit.UTF8_CHARSET);
    getDocument(file);

    assertEquals(LoadTextUtil.AutoDetectionReason.FROM_BYTES, LoadTextUtil.getCharsetAutoDetectionReason(file));

    setContentOnDisk(new File(file.getPath()), null, THREE_RUSSIAN_LETTERS, WINDOWS_1251);
    file.refresh(false, false);

    Assert.assertNull(LoadTextUtil.getCharsetAutoDetectionReason(file));
  }

  public void testSafeToConvert() throws IOException {
    String text = "xxx";
    VirtualFile file = createTempFile("txt", null, text, CharsetToolkit.UTF8_CHARSET);
    byte[] bytes = file.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.ABSOLUTELY, EncodingUtil.isSafeToConvertTo(file, text, bytes, US_ASCII));
    assertEquals(EncodingUtil.Magic8.ABSOLUTELY, EncodingUtil.isSafeToConvertTo(file, text, bytes, WINDOWS_1251));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(file, text, bytes, CharsetToolkit.UTF_16BE_CHARSET));

    String rustext = THREE_RUSSIAN_LETTERS;
    VirtualFile rusfile = createTempFile("txt", null, rustext, CharsetToolkit.UTF8_CHARSET);
    byte[] rusbytes = rusfile.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(rusfile, rustext, rusbytes, US_ASCII));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(rusfile, rustext, rusbytes, WINDOWS_1251));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(rusfile, rustext, rusbytes, CharsetToolkit.UTF_16BE_CHARSET));

    String bomtext = THREE_RUSSIAN_LETTERS;
    VirtualFile bomfile = createTempFile("txt", CharsetToolkit.UTF16LE_BOM, bomtext, CharsetToolkit.UTF_16LE_CHARSET);
    byte[] bombytes = bomfile.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(bomfile, bomtext, bombytes, US_ASCII));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(bomfile, bomtext, bombytes, WINDOWS_1251));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(bomfile, bomtext, bombytes, CharsetToolkit.UTF_16BE_CHARSET));
  }
  public void testSafeToReloadUtf8Bom() throws IOException {
    String text = THREE_RUSSIAN_LETTERS;
    VirtualFile file = createTempFile("txt", CharsetToolkit.UTF8_BOM, text, CharsetToolkit.UTF8_CHARSET);
    byte[] bytes = file.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, US_ASCII));
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, CharsetToolkit.UTF_16LE_CHARSET));
  }
  public void testSafeToReloadUtf8NoBom() throws IOException {
    String text = THREE_RUSSIAN_LETTERS;
    VirtualFile file = createTempFile("txt", null, text, CharsetToolkit.UTF8_CHARSET);
    byte[] bytes = file.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, US_ASCII));
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, CharsetToolkit.UTF_16LE_CHARSET));
  }
  public void testSafeToReloadText() throws IOException {
    String text = "xxx";
    VirtualFile file = createTempFile("txt", null, text, US_ASCII);
    byte[] bytes = file.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.ABSOLUTELY, EncodingUtil.isSafeToReloadIn(file, text, bytes, WINDOWS_1251));
    assertEquals(EncodingUtil.Magic8.ABSOLUTELY, EncodingUtil.isSafeToReloadIn(file, text, bytes, CharsetToolkit.UTF8_CHARSET));
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, CharsetToolkit.UTF_16LE_CHARSET));
  }

  public void testUndoChangeEncoding() throws IOException {
    VirtualFile file = createTempFile("txt", null, "xxx", CharsetToolkit.UTF8_CHARSET);
    file.setCharset(CharsetToolkit.UTF8_CHARSET);
    UIUtil.dispatchAllInvocationEvents();
    assertEquals(CharsetToolkit.UTF8_CHARSET, file.getCharset());

    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    assertNull(documentManager.getCachedDocument(file));

    change(file, WINDOWS_1251);
    assertEquals(WINDOWS_1251, file.getCharset());

    Document document = ObjectUtils.assertNotNull(documentManager.getDocument(file));
    change(file, CharsetToolkit.UTF8_CHARSET);
    assertEquals(CharsetToolkit.UTF8_CHARSET, file.getCharset());

    globalUndo();
    UIUtil.dispatchAllInvocationEvents();

    assertEquals(WINDOWS_1251, file.getCharset());
    ObjectUtils.assertNotNull(document.getText());
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
    VirtualFile file = createTempFile("txt", CharsetToolkit.UTF8_BOM, THREE_RUSSIAN_LETTERS, CharsetToolkit.UTF8_CHARSET);
    file.contentsToByteArray();
    Document document = ObjectUtils.assertNotNull(FileDocumentManager.getInstance().getDocument(file));
    assertEquals(THREE_RUSSIAN_LETTERS, document.getText());
    assertEquals(CharsetToolkit.UTF8_CHARSET, file.getCharset());
    EncodingManager.getInstance().setEncoding(file, CharsetToolkit.UTF_16LE_CHARSET);
    UIUtil.dispatchAllInvocationEvents();

    assertEquals(CharsetToolkit.UTF8_CHARSET, file.getCharset());
  }

  public void testExternalChangeStrippedBOM() throws IOException {
    String text = "text";
    VirtualFile file = createTempFile("txt", CharsetToolkit.UTF8_BOM, text, CharsetToolkit.UTF8_CHARSET);
    file.contentsToByteArray();
    Document document = ObjectUtils.assertNotNull(FileDocumentManager.getInstance().getDocument(file));
    
    assertEquals(text, document.getText());
    assertEquals(CharsetToolkit.UTF8_CHARSET, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF8_BOM, file.getBOM());

    FileUtil.writeToFile(VfsUtilCore.virtualToIoFile(file), text.getBytes(CharsetToolkit.UTF8_CHARSET));
    file.refresh(false, false);
    UIUtil.dispatchAllInvocationEvents();

    assertEquals(text, document.getText());
    assertEquals(CharsetToolkit.UTF8_CHARSET, file.getCharset());
    assertNull(file.getBOM());
  }

  public void testNewFileCreatedInProjectEncoding() throws Exception {
    String oldIDE = EncodingManager.getInstance().getDefaultCharsetName();
    EncodingManager.getInstance().setDefaultCharsetName("UTF-8");
    String oldProject = EncodingProjectManager.getInstance(getProject()).getDefaultCharsetName();
    EncodingProjectManager.getInstance(getProject()).setDefaultCharsetName(WINDOWS_1251.name());
    try {
      PsiFile psiFile = createFile("x.txt", "xx");
      VirtualFile file = psiFile.getVirtualFile();

      assertEquals(WINDOWS_1251, file.getCharset());
    }
    finally {
      EncodingManager.getInstance().setDefaultCharsetName(oldIDE);
      EncodingProjectManager.getInstance(getProject()).setDefaultCharsetName(oldProject);
    }
  }

  public void testNewFileCreatedInProjectEncodingEvenIfItSetToDefault() throws Exception {
    Charset defaultCharset = Charset.defaultCharset();
    String oldIDE = EncodingManager.getInstance().getDefaultCharsetName();
    EncodingManager.getInstance().setDefaultCharsetName(defaultCharset.name().equals("UTF-8") ? "windows-1251" : "UTF-8");
    String oldProject = EncodingProjectManager.getInstance(getProject()).getDefaultCharsetName();
    EncodingProjectManager.getInstance(getProject()).setDefaultCharsetName(""); // default charset
    try {
      PsiFile psiFile = createFile("x.txt", "xx");
      VirtualFile file = psiFile.getVirtualFile();

      assertEquals(EncodingManager.getInstance().getDefaultCharset(), file.getCharset());
    }
    finally {
      EncodingManager.getInstance().setDefaultCharsetName(oldIDE);
      EncodingProjectManager.getInstance(getProject()).setDefaultCharsetName(oldProject);
    }
  }

  private PsiFile createFile(String fileName, String text) throws IOException {
    File dir = createTempDirectory();
    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));

    assert vDir != null : dir;
    return new WriteAction<PsiFile>() {
      @Override
      protected void run(@NotNull Result<PsiFile> result) throws Throwable {
        if (!ModuleRootManager.getInstance(getModule()).getFileIndex().isInSourceContent(vDir)) {
          PsiTestUtil.addSourceContentToRoots(getModule(), vDir);
        }

        final VirtualFile vFile = vDir.createChildData(vDir, fileName);
        VfsUtil.saveText(vFile, text);
        assertNotNull(vFile);
        final PsiFile file = getPsiManager().findFile(vFile);
        assertNotNull(file);
        result.setResult(file);
      }
    }.execute().getResultObject();
  }

  public void testTheDefaultProjectEncodingIfNotSpecifiedShouldBeIDEEncoding() throws Exception {
    String differentFromDefault = Charset.defaultCharset().name().equals("UTF-8") ? "windows-1251" : "UTF-8";
    String oldIDE = EncodingManager.getInstance().getDefaultCharsetName();
    try {
      EncodingManager.getInstance().setDefaultCharsetName(differentFromDefault);

      File temp = createTempDirectory();
      VirtualFile tempDir = ObjectUtils.assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(temp));

      final Project newProject =
        ObjectUtils.assertNotNull(ProjectManagerEx.getInstanceEx().newProject("new", tempDir.getPath(), false, false));
      Disposer.register(getTestRootDisposable(), () -> ApplicationManager.getApplication().runWriteAction(() -> Disposer.dispose(newProject)));
      PlatformTestUtil.saveProject(newProject);

      Charset newProjectEncoding = EncodingProjectManager.getInstance(newProject).getDefaultCharset();
      assertEquals(differentFromDefault, newProjectEncoding.name());

      PsiFile psiFile = createFile("x.txt", "xx");
      VirtualFile file = psiFile.getVirtualFile();

      assertEquals(differentFromDefault, file.getCharset().name());
    }
    finally {
      EncodingManager.getInstance().setDefaultCharsetName(oldIDE);
    }
  }

  public void testFileMustNotLoadInWrongEncodingIfAccessedBeforeProjectOpen() {
    VirtualFile dir = find("newEncoding");
    VirtualFile file = ObjectUtils.assertNotNull(dir.findFileByRelativePath("src/xxx.txt"));

    Document document = ObjectUtils.assertNotNull(FileDocumentManager.getInstance().getDocument(file));
    ObjectUtils.assertNotNull(document.getText());
    UIUtil.dispatchAllInvocationEvents();

    Project newEncodingProject = ObjectUtils.assertNotNull(ProjectUtil.openProject(dir.getPath(), null, false));
    UIUtil.dispatchAllInvocationEvents();
    try {
      assertEquals(US_ASCII, file.getCharset());
    }
    finally {
      ProjectUtil.closeAndDispose(newEncodingProject);
    }
  }

  public void testFileInsideJarCorrectlyHandlesBOM() throws IOException {
    File tmpDir = createTempDirectory();
    File jar = new File(tmpDir, "x.jar");
    String text = "update";
    byte[] bytes = ArrayUtil.mergeArrays(CharsetToolkit.UTF16BE_BOM, text.getBytes(CharsetToolkit.UTF_16BE_CHARSET));
    String name = "sjkdhfksjdf";
    IoTestUtil.createTestJar(jar, Collections.singletonList(Pair.create(name, bytes)));
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
    assertNotNull(vFile);
    VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(vFile);
    assertNotNull(jarRoot);

    VirtualFile file = jarRoot.findChild(name);
    assertNotNull(file);
    assertEquals(CharsetToolkit.UTF_16BE_CHARSET, file.getCharset());
    assertEquals(PlainTextFileType.INSTANCE, file.getFileType());
    assertEquals(text, LoadTextUtil.loadText(file).toString());
    try (InputStream stream = file.getInputStream()) {
      String loaded = new String(FileUtil.loadBytes(stream), CharsetToolkit.UTF_16BE_CHARSET);
      assertEquals(text, loaded);
    }
  }

  public void testBigFileInsideJarCorrectlyHandlesBOM() throws IOException {
    File tmpDir = createTempDirectory();
    File jar = new File(tmpDir, "x.jar");
    String bigText = StringUtil.repeat("u", FileUtilRt.LARGE_FOR_CONTENT_LOADING+1);
    byte[] utf16beBytes = ArrayUtil.mergeArrays(CharsetToolkit.UTF16BE_BOM, bigText.getBytes(CharsetToolkit.UTF_16BE_CHARSET));
    String name = "sjkdhfksjdf";
    IoTestUtil.createTestJar(jar, Collections.singletonList(Pair.create(name, utf16beBytes)));
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
    assertNotNull(vFile);
    VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(vFile);
    assertNotNull(jarRoot);

    VirtualFile file = jarRoot.findChild(name);
    assertNotNull(file);
    try (InputStream stream = file.getInputStream()) {
      String loaded = new String(FileUtil.loadBytes(stream, 8192*2), CharsetToolkit.UTF_16BE_CHARSET);
      assertEquals(bigText.substring(0, 8192), loaded);
    }
    assertEquals(PlainTextFileType.INSTANCE, file.getFileType());
    assertEquals(CharsetToolkit.UTF_16BE_CHARSET, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF16BE_BOM, file.getBOM());
    // should not load the entire file anyway
    //assertEquals(text, LoadTextUtil.loadText(file).toString());
  }

  public void testSevenBitFileTextOptimisationWorks() throws IOException {
    PsiFile file = createFile("x.txt", "a,mvnsxkjfhswr\nsdfsdf\n");
    VirtualFile virtualFile = file.getVirtualFile();
    CharSequence loaded = LoadTextUtil.loadText(virtualFile);
    assertInstanceOf(loaded, ByteArrayCharSequence.class);
  }

  public void testUTF16LEWithNoBOMIsAThing() throws IOException {
    String text = "text";
    File file = createTempFile("a.txt", text);
    setContentOnDisk(file, null, text, CharsetToolkit.UTF_16LE_CHARSET);

    VirtualFile vFile = ObjectUtils.assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file));
    vFile.setCharset(CharsetToolkit.UTF_16LE_CHARSET);
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

      manager.setBOMForNewUtf8Files(EncodingProjectManagerImpl.BOMForNewUTF8Files.ALWAYS);
      VirtualFile file2 = createFile("x2.txt", "xx").getVirtualFile();
      assertArrayEquals(CharsetToolkit.UTF8_BOM, file2.getBOM());

      manager.setBOMForNewUtf8Files(EncodingProjectManagerImpl.BOMForNewUTF8Files.WINDOWS_ONLY);
      VirtualFile file3 = createFile("x3.txt", "xx").getVirtualFile();
      byte[] expected = SystemInfo.isWindows ? CharsetToolkit.UTF8_BOM : null;
      assertArrayEquals(expected, file3.getBOM());

      manager.setBOMForNewUtf8Files(EncodingProjectManagerImpl.BOMForNewUTF8Files.NEVER);
      VirtualFile file4 = createFile("x4.txt", "xx").getVirtualFile();
      assertNull(file4.getBOM());
    }
    finally {
      manager.setBOMForNewUtf8Files(old);
      manager.setDefaultCharsetName(oldProject);
    }
  }

  public void testBigFileAutoDetectedAsTextMustDetermineItsEncodingFromTheWholeTextToMinimizePossibilityOfUmlautInTheEndMisdetectionError() {
    VirtualFile vTestRoot = getTestRoot();
    VirtualFile file = vTestRoot.findChild("BIGCHANGES");
    assertNotNull(file);

    assertNull(file.getBOM());
    assertEquals(CharsetToolkit.UTF8_CHARSET, file.getCharset());
  }
}
