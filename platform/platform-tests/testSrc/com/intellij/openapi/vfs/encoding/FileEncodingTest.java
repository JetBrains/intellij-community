// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.encoding;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.components.ComponentManagerEx;
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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.limits.FileSizeLimit;
import com.intellij.openapi.wm.impl.status.EncodingPanel;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.copy.CopyFilesOrDirectoriesHandler;
import com.intellij.testFramework.*;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.text.ByteArrayCharSequence;
import com.intellij.util.text.XmlCharsetDetector;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.*;

import javax.swing.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.*;

@RunsInEdt
@SuppressWarnings({"UnnecessaryUnicodeEscape", "NonAsciiCharacters"})
public class FileEncodingTest implements TestDialog {
  private static final Charset WINDOWS_1251 = CharsetToolkit.WIN_1251_CHARSET;
  private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
  private static final String UTF8_XML_PROLOG = prolog(StandardCharsets.UTF_8);
  private static final byte[] NO_BOM = ArrayUtilRt.EMPTY_BYTE_ARRAY;
  @Language("XML")
  private static final String XML_TEST_BODY = """
    <web-app>
    <!--\u043f\u0430\u043f\u0430-->
    </web-app>""";
  private static final String THREE_RUSSIAN_LETTERS = "\u043F\u0440\u0438\u0432\u0435\u0442";

  @ClassRule public static final EdtRule edt = new EdtRule();

  @Rule public TempDirectory tempDir = new TempDirectory();
  @Rule public DisposableRule disposable = new DisposableRule();
  @Rule public ProjectRule project = new ProjectRule();

  private TestDialog myOldTestDialogValue;

  @Override
  public int show(@NotNull String message) {
    return 0;
  }

  @Before
  public void setUp() throws Exception {
    myOldTestDialogValue = TestDialogManager.setTestDialog(this);
    EncodingProjectManager.getInstance(getProject()).setDefaultCharsetName(defaultProjectEncoding().name());
  }

  @After
  public void tearDown() throws Exception {
    TestDialogManager.setTestDialog(myOldTestDialogValue);
  }

  private static Charset defaultProjectEncoding() {
    return StandardCharsets.US_ASCII;  // just for the sake of testing something different from utf-8
  }

  private static String prolog(Charset charset) {
    return "<?xml version=\"1.0\" encoding=\"" + charset.name() + "\"?>\n";
  }

  private static Document getDocument(VirtualFile file) {
    return FileDocumentManager.getInstance().getDocument(file);
  }

  private static VirtualFile getVirtualFile(Path path) {
    return requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path));
  }

  private static VirtualFile getTestRoot() {
    File testRoot = new File(PathManagerEx.getCommunityHomePath(), "platform/platform-tests/testData/vfs/encoding");
    return LocalFileSystem.getInstance().findFileByIoFile(testRoot);
  }

  private static VirtualFile find(String name) {
    return requireNonNull(getTestRoot().findChild(name));
  }

  private static void setText(Document document, String text) {
    ApplicationManager.getApplication().runWriteAction(() -> document.setText(text));
  }

  private Disposable getTestRootDisposable() {
    return disposable.getDisposable();
  }

  private Project getProject() {
    return project.getProject();
  }

  private Module getModule() {
    return project.getModule();
  }

  private VirtualFile createFile(String fileName, String text) throws IOException {
    Path dir = tempDir.newDirectoryPath();
    VirtualFile vDir = getVirtualFile(dir);
    return WriteAction.compute(() -> {
      if (!ModuleRootManager.getInstance(getModule()).getFileIndex().isInSourceContent(vDir)) {
        PsiTestUtil.addSourceContentToRoots(getModule(), vDir);
      }
      VirtualFile vFile = vDir.createChildData(vDir, fileName);
      LoadTextUtil.write(getProject(), vFile, this, text, -1);
      assertNotNull(PsiManager.getInstance(getProject()).findFile(vFile));
      return vFile;
    });
  }

  private VirtualFile createTempFile(String ext, byte[] BOM, String content, Charset charset) throws IOException {
    Path file = Files.createTempFile(tempDir.getRootPath(), "copy", '.' + ext);
    try (OutputStream stream = Files.newOutputStream(file)) {
      stream.write(BOM);
      stream.write(content.getBytes(charset));
    }
    Disposer.register(getTestRootDisposable(), () -> {
      try {
        Files.delete(file);
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
    return getVirtualFile(file);
  }

  @Test
  public void testWin1251() {
    VirtualFile vTestRoot = getTestRoot();
    VirtualFile xml = vTestRoot.findChild("xWin1251.xml");

    String expected = prolog(WINDOWS_1251) + XML_TEST_BODY;
    String text = getDocument(xml).getText();

    assertEquals(expected, text);
  }

  @Test
  public void testXmlProlog() throws IOException {
    VirtualFile vTestRoot = getTestRoot();
    VirtualFile xml = requireNonNull(vTestRoot.findChild("xNotepadUtf8.xml"));

    String expected = UTF8_XML_PROLOG + XML_TEST_BODY;
    String text = getDocument(xml).getText();

    if (!expected.equals(text)) {
      System.err.print("expected = ");
      for (int i = 0; i < 50; i++) {
        char c = expected.charAt(i);
        System.err.print(Integer.toHexString(c) + ", ");
      }
      System.err.println();
      System.err.print("expected bytes = ");
      byte[] expectedBytes = FileUtil.loadFileBytes(new File(xml.getPath()));
      for (int i = 0; i < 50; i++) {
        byte c = expectedBytes[i];
        System.err.print(Integer.toHexString(c) + ", ");
      }
      System.err.println();

      System.err.print("text = ");
      for (int i = 0; i < 50; i++) {
        char c = text.charAt(i);
        System.err.print(Integer.toHexString(c) + ", ");
      }
      System.err.println();
      System.err.print("text bytes = ");
      byte[] textBytes = xml.contentsToByteArray();
      for (int i = 0; i < 50; i++) {
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

  @Test
  public void testChangeToUtfProlog() throws IOException {
    VirtualFile src = find("xWin1251.xml");
    Path file = tempDir.getRootPath().resolve("copy.xml");
    Files.copy(src.toNioPath(), file, StandardCopyOption.REPLACE_EXISTING);

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      VirtualFile xml = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
      Document document = getDocument(xml);

      setText(document, UTF8_XML_PROLOG + XML_TEST_BODY);
      FileDocumentManager.getInstance().saveAllDocuments();

      byte[] savedBytes = Files.readAllBytes(file);
      String saved = new String(savedBytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
      String expected = (UTF8_XML_PROLOG + XML_TEST_BODY).replace("\r\n", "\n");

      assertEquals(expected, saved);
    });
  }

  @Test
  public void testDefaultHtml() {
    VirtualFile file = find("defaultHtml.html");

    assertEquals(EncodingProjectManager.getInstance(getProject()).getDefaultCharset(), file.getCharset());
  }

  @Test
  public void testSevenBitTextMustBeDetectedAsDefaultProjectEncodingInsteadOfUsAscii() throws IOException {
    VirtualFile file = createTempFile("txt", NO_BOM, "xxx\nxxx", WINDOWS_1251);

    assertEquals(defaultProjectEncoding(), file.getCharset());
  }

  @Test
  public void testTrickyProlog() {
    VirtualFile xml = find("xTrickyProlog.xml");

    assertEquals("Big5", xml.getCharset().name());
  }

  @Test
  public void testDefaultXml() {
    VirtualFile xml = find("xDefault.xml");

    assertEquals(StandardCharsets.UTF_8, xml.getCharset());
  }

  @Test
  public void testIbm866() {
    VirtualFile xml = find("xIbm866.xml");

    String expected = prolog(Charset.forName("IBM866")) + XML_TEST_BODY;
    String text = getDocument(xml).getText();

    assertEquals(expected, text);
  }

  @Test
  public void testUTF16BOM() throws IOException {
    VirtualFile file = find("UTF16BOM.txt");
    File source = new File(file.getPath());
    byte[] bytesSource = FileUtil.loadFileBytes(source);
    assertTrue(Arrays.toString(bytesSource), CharsetToolkit.hasUTF16LEBom(bytesSource));
    assertEquals("[-1, -2, 31, 4, 64, 4, 56, 4]", Arrays.toString(bytesSource));

    Document document = getDocument(file);
    String text = document.getText();
    assertEquals("\u041f\u0440\u0438", text);

    Path copy = tempDir.getRootPath().resolve("copy.txt");
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

  @Test
  public void testMetaHttpEquivHtml() throws IOException {
    doHtmlTest(
      "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1252\">",
      "<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\">"
    );
  }

  @Test
  public void testMetaCharsetHtml5() throws IOException {
    doHtmlTest(
      "<meta charset =\"windows-1252\">",
      "<meta charset =\"utf-8\">"
    );
  }

  private void doHtmlTest(@Language("HTML") String metaWithWindowsEncoding, @Language("HTML") String metaWithUtf8Encoding) throws IOException {
    VirtualFile
      file = createTempFile("html", NO_BOM, "<html><head>" + metaWithWindowsEncoding + "</head>" + THREE_RUSSIAN_LETTERS + "</html>", WINDOWS_1252);
    assertEquals(WINDOWS_1252, file.getCharset());

    Document document = getDocument(file);
    @Language("HTML")
    String text = "<html><head>" + metaWithUtf8Encoding + "</head>" + THREE_RUSSIAN_LETTERS + "</html>";
    setText(document, text);
    FileDocumentManager.getInstance().saveAllDocuments();
    assertEquals(StandardCharsets.UTF_8, file.getCharset());
  }

  @Test
  public void testHtmlEncodingPreferBOM() throws IOException {
    @Language("HTML")
    String content = """
      <html>
      <head><meta http-equiv="Content-Type" content="text/html; charset=windows-1252"></head>
      %s
      </html>""".formatted(THREE_RUSSIAN_LETTERS);
    VirtualFile file = createTempFile("html", CharsetToolkit.UTF8_BOM, content, WINDOWS_1252);
    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF8_BOM, file.getBOM());
  }

  @Test
  public void testHtmlEncodingMustBeCaseInsensitive() throws IOException {
    @Language("HTML")
    String content = """
      <html>
      <head>
      <meta http-equiv="content-type" content="text/html;charset=us-ascii">\s
      </head>
      <body>
      xyz
      </body>
      </html>""";
    VirtualFile file = createTempFile("html", NO_BOM, content, WINDOWS_1252);
    assertEquals(StandardCharsets.US_ASCII, file.getCharset());
  }

  @Test
  public void testHtmlContentAttributeOrder() throws IOException {
    @Language("HTML")
    String content = """
      <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
      \t\t"http://www.w3.org/TR/html4/loose.dtd">
      <html> <head>
      \t<meta content="text/html;charset=US-ASCII" http-equiv="Content-Type">
      </head> </html>""";
    VirtualFile file = createTempFile("html", NO_BOM, content, WINDOWS_1252);
    assertEquals(StandardCharsets.US_ASCII, file.getCharset());
  }

  @Test
  public void testXHtmlStuff() throws IOException {
    @Language("HTML")
    String text = "<xxx>\n</xxx>";
    VirtualFile file = createTempFile("xhtml", NO_BOM, text, WINDOWS_1252);

    Document document = getDocument(file);
    setText(document, prolog(WINDOWS_1251) + document.getText());
    FileDocumentManager.getInstance().saveAllDocuments();
    assertEquals(WINDOWS_1251, file.getCharset());

    setText(document, prolog(StandardCharsets.US_ASCII) + "\n<xxx></xxx>");
    FileDocumentManager.getInstance().saveAllDocuments();
    assertEquals(StandardCharsets.US_ASCII, file.getCharset());

    text = prolog(WINDOWS_1252) + "\n<xxx>\n</xxx>";
    file = createTempFile("xhtml", NO_BOM, text, WINDOWS_1252);
    assertEquals(WINDOWS_1252, file.getCharset());
  }

  @Test
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
    EncodingProjectManager.getInstance(getProject()).setEncoding(file, StandardCharsets.US_ASCII);
    UIUtil.dispatchAllInvocationEvents();
    assertEquals(StandardCharsets.US_ASCII, file.getCharset());
    UIUtil.dispatchAllInvocationEvents();

    assertTrue(changed[0]); //reloaded again

    //after 'save as US_ASCII' file on disk changed
    bytes = FileUtil.loadFileBytes(ioFile);
    assertArrayEquals(text.getBytes(StandardCharsets.US_ASCII), bytes);
  }

  @Test
  public void testCopyMove() throws IOException {
    VirtualFile vDir1 = getVirtualFile(tempDir.newDirectoryPath("dir1"));
    VirtualFile vDir2 = getVirtualFile(tempDir.newDirectoryPath("dir2"));

    EncodingProjectManager.getInstance(getProject()).setEncoding(vDir1, WINDOWS_1251);
    EncodingProjectManager.getInstance(getProject()).setEncoding(vDir2, StandardCharsets.US_ASCII);
    UIUtil.dispatchAllInvocationEvents();

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      VirtualFile xxx = vDir1.createChildData(this, "xxx.txt");
      HeavyPlatformTestCase.setFileText(xxx, THREE_RUSSIAN_LETTERS);
      assertEquals(WINDOWS_1251, xxx.getCharset());
      VirtualFile copied = xxx.copy(this, vDir2, "xxx2.txt");
      assertEquals(WINDOWS_1251, copied.getCharset());

      VirtualFile xus = vDir2.createChildData(this, "xxx-us.txt");
      HeavyPlatformTestCase.setFileText(xus, THREE_RUSSIAN_LETTERS);
      assertEquals(StandardCharsets.US_ASCII, xus.getCharset());

      xus.move(this, vDir1);
      assertEquals(StandardCharsets.US_ASCII, xus.getCharset());
    });
  }

  @Test
  public void testCopyNested() throws IOException {
    VirtualFile vDir1 = getVirtualFile(tempDir.newDirectoryPath("dir1"));
    VirtualFile vDir2 = getVirtualFile(tempDir.newDirectoryPath("dir2"));

    EncodingProjectManager.getInstance(getProject()).setEncoding(vDir1, WINDOWS_1251);
    EncodingProjectManager.getInstance(getProject()).setEncoding(vDir2, StandardCharsets.US_ASCII);
    UIUtil.dispatchAllInvocationEvents();

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      VirtualFile winF = vDir1.createChildData(this, "xxx.txt");

      PsiDirectory psiDir1 = requireNonNull(PsiManager.getInstance(getProject()).findDirectory(vDir1));
      PsiDirectory psiDir2 = requireNonNull(PsiManager.getInstance(getProject()).findDirectory(vDir2));
      CopyFilesOrDirectoriesHandler.copyToDirectory(psiDir1, psiDir1.getName(), psiDir2);
      VirtualFile winFCopy = requireNonNull(psiDir2.getVirtualFile().findFileByRelativePath(psiDir1.getName() + "/" + winF.getName()));
      assertEquals(WINDOWS_1251, winFCopy.getCharset());
      VirtualFile dir1Copy = psiDir2.getVirtualFile().findChild(psiDir1.getName());
      assertEquals(WINDOWS_1251, EncodingProjectManager.getInstance(getProject()).getEncoding(dir1Copy, false));
      assertNull(EncodingProjectManager.getInstance(getProject()).getEncoding(winFCopy, false));
    });
  }

  @Test
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

  @Test
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

  @Test
  public void testVFileGetInputStreamIsBOMAware() throws IOException {
    VirtualFile file = find("UTF16BOM.txt");

    String vfsLoad = VfsUtilCore.loadText(file);
    assertEquals("\u041f\u0440\u0438", vfsLoad);

    CharSequence ltuText = LoadTextUtil.loadText(file);
    assertEquals(vfsLoad, ltuText.toString());

    assertArrayEquals(CharsetToolkit.UTF16LE_BOM, file.getBOM());
  }
  @Test
  public void testSetCharsetAfter() throws IOException {
    VirtualFile file = find("UTF16LE_NO_BOM.txt");
    file.setCharset(StandardCharsets.UTF_16LE);
    file.setBOM(null);
    String vfsLoad = VfsUtilCore.loadText(file);
    assertEquals("\u041f\u0440\u0438\u0432\u0435\u0442", vfsLoad);
    assertNull(file.getBOM());
  }

  @Test
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

  @Test
  public void testConvertNotAvailableForHtml() throws IOException {
    @org.intellij.lang.annotations.Language("HTML")
    String content = "<html><head><meta content=\"text/html; charset=utf-8\" http-equiv=\"content-type\"></head>" +
                     "<body>" + THREE_RUSSIAN_LETTERS +
                     "</body></html>";
    VirtualFile virtualFile = createTempFile("html", NO_BOM, content, StandardCharsets.UTF_8);
    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    assertNotNull(document);
    FileDocumentManager.getInstance().saveAllDocuments();
    FileType fileType = virtualFile.getFileType();
    assertEquals(FileTypeManager.getInstance().getStdFileType("HTML"), fileType);
    Charset fromType = ((LanguageFileType)fileType).extractCharsetFromFileContent(getProject(), virtualFile, (CharSequence)content);
    assertEquals(StandardCharsets.UTF_8, fromType);
    String fromProlog = XmlCharsetDetector.extractXmlEncodingFromProlog(content);
    assertNull(fromProlog);
    Charset charsetFromContent = EncodingManagerImpl.computeCharsetFromContent(virtualFile);
    Document docFromVF = FileDocumentManager.getInstance().getDocument(virtualFile);
    Charset extractedCharset = ((LanguageFileType)fileType).extractCharsetFromFileContent(getProject(), virtualFile, document.getImmutableCharSequence());
    assertEquals("docFromVF: " + docFromVF + "; fileType=" + fileType + "; extractedCharset=" + extractedCharset, StandardCharsets.UTF_8, charsetFromContent);
    EncodingUtil.FailReason result = EncodingUtil.checkCanConvert(virtualFile);
    assertEquals(EncodingUtil.FailReason.BY_FILE, result);
  }

  @Test
  public void testConvertReload() throws IOException {
    VirtualFile file = createTempFile("txt", CharsetToolkit.UTF16BE_BOM, THREE_RUSSIAN_LETTERS, StandardCharsets.UTF_16BE);

    assertEquals(StandardCharsets.UTF_16BE, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF16BE_BOM, file.getBOM());

    Document document = getDocument(file);

    byte[] bytes = file.contentsToByteArray();
    String text = document.getText();
    assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, WINDOWS_1251));
    assertSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, StandardCharsets.US_ASCII));
    EncodingUtil.FailReason result = EncodingUtil.checkCanReload(file, null);
    assertEquals(EncodingUtil.FailReason.BY_BOM, result);

    EncodingUtil.saveIn(getProject(), document, null, file, WINDOWS_1251);
    bytes = file.contentsToByteArray();
    assertEquals(WINDOWS_1251, file.getCharset());
    assertNull(file.getBOM());

    assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, StandardCharsets.UTF_16LE));
    assertSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, StandardCharsets.US_ASCII));
    result = EncodingUtil.checkCanReload(file, null);
    assertNull(result);

    EncodingUtil.saveIn(getProject(), document, null, file, StandardCharsets.UTF_16LE);
    bytes = file.contentsToByteArray();
    assertEquals(StandardCharsets.UTF_16LE, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF16LE_BOM, file.getBOM());

    assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, WINDOWS_1251));
    assertSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, StandardCharsets.US_ASCII));
    result = EncodingUtil.checkCanReload(file, null);
    assertNotNull(result);

    text = "xxx";
    setText(document, text);
    assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, WINDOWS_1251));
    assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, StandardCharsets.US_ASCII));

    FileDocumentManager.getInstance().saveAllDocuments();
    bytes = file.contentsToByteArray();
    result = EncodingUtil.checkCanReload(file, null);
    assertNotNull(result);

    text = "qqq";
    setText(document, text);
    assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, WINDOWS_1251));
    assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, StandardCharsets.US_ASCII));

    EncodingUtil.saveIn(getProject(), document, null, file, StandardCharsets.US_ASCII);
    bytes = file.contentsToByteArray();
    assertEquals(StandardCharsets.US_ASCII, file.getCharset());
    assertNull(file.getBOM());

    assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, WINDOWS_1251));
    result = EncodingUtil.checkCanReload(file, null);
    assertNull(result);
  }

  @Test
  public void testSetEncodingForDirectoryChangesEncodingsForEvenNotLoadedFiles() throws IOException {
    EncodingProjectManager.getInstance(getProject()).setEncoding(null, StandardCharsets.UTF_8);
    UIUtil.dispatchAllInvocationEvents();

    File fc = FileUtil.createTempDirectory("", "");
    VirtualFile root = requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fc));
    PsiTestUtil.addContentRoot(getModule(), root);

    VirtualFile file = HeavyPlatformTestCase.createChildData(root, "win.txt");
    Files.writeString(file.toNioPath(), THREE_RUSSIAN_LETTERS, WINDOWS_1251);
    file.refresh(false, false);

    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    assertNull(file.getBOM());


    EncodingProjectManager.getInstance(getProject()).setEncoding(root, WINDOWS_1251);
    UIUtil.dispatchAllInvocationEvents();

    assertEquals(WINDOWS_1251, file.getCharset());
    assertNull(file.getBOM());
  }

  @Test
  public void testExternalChangeClearsAutoDetectedFromBytesFlag() throws IOException {
    VirtualFile file = createTempFile("txt", NO_BOM, THREE_RUSSIAN_LETTERS, StandardCharsets.UTF_8);
    getDocument(file);

    assertEquals(LoadTextUtil.AutoDetectionReason.FROM_BYTES, LoadTextUtil.getCharsetAutoDetectionReason(file));

    Files.writeString(file.toNioPath(), THREE_RUSSIAN_LETTERS, WINDOWS_1251);
    file.refresh(false, false);

    assertNull(LoadTextUtil.getCharsetAutoDetectionReason(file));
  }

  @Test
  public void testSafeToConvert() throws IOException {
    String text = "xxx";
    VirtualFile file = createTempFile("txt", NO_BOM, text, StandardCharsets.UTF_8);
    byte[] bytes = file.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.ABSOLUTELY, EncodingUtil.isSafeToConvertTo(file, text, bytes, StandardCharsets.US_ASCII));
    assertEquals(EncodingUtil.Magic8.ABSOLUTELY, EncodingUtil.isSafeToConvertTo(file, text, bytes, WINDOWS_1251));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(file, text, bytes, StandardCharsets.UTF_16BE));

    String cyrText = THREE_RUSSIAN_LETTERS;
    VirtualFile cyrFile = createTempFile("txt", NO_BOM, cyrText, StandardCharsets.UTF_8);
    byte[] cyrBytes = cyrFile.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(cyrFile, cyrText, cyrBytes, StandardCharsets.US_ASCII));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(cyrFile, cyrText, cyrBytes, WINDOWS_1251));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(cyrFile, cyrText, cyrBytes, StandardCharsets.UTF_16BE));

    String bomText = THREE_RUSSIAN_LETTERS;
    VirtualFile bomFile = createTempFile("txt", CharsetToolkit.UTF16LE_BOM, bomText, StandardCharsets.UTF_16LE);
    byte[] bomBytes = bomFile.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(bomFile, bomText, bomBytes, StandardCharsets.US_ASCII));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(bomFile, bomText, bomBytes, WINDOWS_1251));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(bomFile, bomText, bomBytes, StandardCharsets.UTF_16BE));
  }

  @Test
  public void testSafeToReloadUtf8Bom() throws IOException {
    String text = THREE_RUSSIAN_LETTERS;
    VirtualFile file = createTempFile("txt", CharsetToolkit.UTF8_BOM, text, StandardCharsets.UTF_8);
    byte[] bytes = file.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, StandardCharsets.US_ASCII));
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, StandardCharsets.UTF_16LE));
  }

  @Test
  public void testSafeToReloadUtf8NoBom() throws IOException {
    String text = THREE_RUSSIAN_LETTERS;
    VirtualFile file = createTempFile("txt", NO_BOM, text, StandardCharsets.UTF_8);
    byte[] bytes = file.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, StandardCharsets.US_ASCII));
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, StandardCharsets.UTF_16LE));
  }

  @Test
  public void testSafeToReloadText() throws IOException {
    String text = "xxx";
    VirtualFile file = createTempFile("txt", NO_BOM, text, StandardCharsets.US_ASCII);
    byte[] bytes = file.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.ABSOLUTELY, EncodingUtil.isSafeToReloadIn(file, text, bytes, WINDOWS_1251));
    assertEquals(EncodingUtil.Magic8.ABSOLUTELY, EncodingUtil.isSafeToReloadIn(file, text, bytes, StandardCharsets.UTF_8));
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, StandardCharsets.UTF_16LE));
  }

  @Test
  public void testMustBeSafeToReloadISO8859TextMistakenlyLoadedInUTF8() throws IOException {
    @SuppressWarnings("SpellCheckingInspection") String isoText = "No se ha encontrado ningún puesto con ese criterio de búsqueda";
    VirtualFile file = createTempFile("txt", NO_BOM, isoText, StandardCharsets.ISO_8859_1);
    byte[] bytes = isoText.getBytes(StandardCharsets.ISO_8859_1);
    file.setCharset(StandardCharsets.UTF_8);
    String utfText = new String(bytes, StandardCharsets.UTF_8);
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToReloadIn(file, utfText, bytes, StandardCharsets.ISO_8859_1));
  }

  @Test
  public void testUndoChangeEncoding() throws IOException {
    VirtualFile file = createTempFile("txt", NO_BOM, "xxx", StandardCharsets.UTF_8);
    file.setCharset(StandardCharsets.UTF_8);
    UIUtil.dispatchAllInvocationEvents();
    assertEquals(StandardCharsets.UTF_8, file.getCharset());

    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    assertNull(documentManager.getCachedDocument(file));

    change(file, WINDOWS_1251);
    assertEquals(WINDOWS_1251, file.getCharset());

    Document document = requireNonNull(documentManager.getDocument(file));
    change(file, StandardCharsets.UTF_8);
    assertEquals(StandardCharsets.UTF_8, file.getCharset());

    globalUndo();
    UIUtil.dispatchAllInvocationEvents();

    assertEquals(WINDOWS_1251, file.getCharset());
    requireNonNull(document.getText());
  }

  private static void change(VirtualFile file, Charset charset) throws IOException {
    new ChangeFileEncodingAction().chosen(getDocument(file), null, file, file.contentsToByteArray(), charset);
  }

  private void globalUndo() {
    UndoManager myManager = UndoManager.getInstance(getProject());
    assertTrue("undo is not available", myManager.isUndoAvailable(null));
    myManager.undo(null);
  }

  @Test
  public void testMustBeAbleForFileAccidentallyLoadedInUTF16ToReloadBackToUtf8() throws IOException {
    VirtualFile file = createTempFile("txt", NO_BOM, "xxx", StandardCharsets.UTF_8);
    file.setCharset(StandardCharsets.UTF_8);
    UIUtil.dispatchAllInvocationEvents();
    Document document = FileDocumentManager.getInstance().getDocument(file);
    String oldText = document.getText();
    assertEquals(StandardCharsets.UTF_8, file.getCharset());

    // yeah, it was not safe to reload in utf16
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, document.getText(), file.contentsToByteArray(), StandardCharsets.UTF_16BE));
    // but it happened
    EncodingUtil.reloadIn(file, StandardCharsets.UTF_16BE, getProject());
    UIUtil.dispatchAllInvocationEvents();
    assertEquals(StandardCharsets.UTF_16BE, file.getCharset());
    assertEquals(CharsetToolkit.UTF16BE_BOM, file.getBOM());

    // and back
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToReloadIn(file, document.getText(), file.contentsToByteArray(), StandardCharsets.UTF_8));
    EncodingUtil.reloadIn(file, StandardCharsets.UTF_8, getProject());
    UIUtil.dispatchAllInvocationEvents();
    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    assertEquals(oldText,  document.getText());
  }

  @Test
  public void testCantReloadBOMDetected() throws IOException {
    VirtualFile file = createTempFile("txt", CharsetToolkit.UTF8_BOM, THREE_RUSSIAN_LETTERS, StandardCharsets.UTF_8);
    file.contentsToByteArray();
    Document document = requireNonNull(FileDocumentManager.getInstance().getDocument(file));
    assertEquals(THREE_RUSSIAN_LETTERS, document.getText());
    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    EncodingProjectManager.getInstance(getProject()).setEncoding(file, StandardCharsets.UTF_16LE);
    UIUtil.dispatchAllInvocationEvents();

    assertEquals(StandardCharsets.UTF_8, file.getCharset());
  }

  @Test
  public void testExternalChangeStrippedBOM() throws IOException {
    String text = "text";
    VirtualFile file = createTempFile("txt", CharsetToolkit.UTF8_BOM, text, StandardCharsets.UTF_8);
    file.contentsToByteArray();
    Document document = requireNonNull(FileDocumentManager.getInstance().getDocument(file));

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

  @Test
  public void testNewFileCreatedInProjectEncoding() throws IOException {
    EditorTestUtil.saveEncodingsIn(getProject(), StandardCharsets.UTF_8, WINDOWS_1251, () -> {
      VirtualFile file = createFile("x.txt", "xx");
      assertEquals(WINDOWS_1251, file.getCharset());
    });
  }

  @Test
  public void testNewFileCreatedInProjectEncodingEvenIfItSetToDefault() throws IOException {
    Charset newCS = Charset.defaultCharset().name().equals("UTF-8") ? WINDOWS_1251 : StandardCharsets.UTF_8;
    EditorTestUtil.saveEncodingsIn(getProject(), newCS, null, () -> {
      VirtualFile file = createFile("x.txt", "xx");
      assertEquals(EncodingProjectManager.getInstance(getProject()).getDefaultCharset(), file.getCharset());
    });
  }

  @Test
  public void testTheDefaultProjectEncodingIfNotSpecifiedShouldBeIDEEncoding() throws Exception {
    Charset differentFromDefault = Charset.defaultCharset().equals(WINDOWS_1252) ? WINDOWS_1251 : WINDOWS_1252;
    String oldIDE = EncodingManager.getInstance().getDefaultCharsetName();
    try {
      EncodingManager.getInstance().setDefaultCharsetName(differentFromDefault.name());

      Path temp = tempDir.newDirectoryPath();
      VirtualFile tempDir = getVirtualFile(temp);

      Project newProject = ProjectManagerEx.getInstanceEx().openProject(temp, OpenProjectTaskBuilderKt.createTestOpenProjectOptions());
      if (ApplicationManager.getApplication().isDispatchThread()) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      }
      try {
        PlatformTestUtil.saveProject(newProject);

        Charset newProjectEncoding = EncodingProjectManager.getInstance(newProject).getDefaultCharset();
        assertEquals(differentFromDefault, newProjectEncoding);

        VirtualFile vFile = WriteCommandAction.runWriteCommandAction(newProject, (ThrowableComputable<VirtualFile, IOException>)() -> {
          Module newModule = ModuleManager.getInstance(newProject).newModule(temp, "blah");
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

  @Test
  public void testFileMustNotLoadInWrongEncodingIfAccessedBeforeProjectOpen() {
    VirtualFile dir = find("newEncoding");
    VirtualFile file = requireNonNull(dir.findFileByRelativePath("src/xxx.txt"));

    Document document = requireNonNull(FileDocumentManager.getInstance().getDocument(file));
    assertNotNull(document.getText());
    UIUtil.dispatchAllInvocationEvents();

    Project newEncodingProject = PlatformTestUtil.loadAndOpenProject(dir.toNioPath(), getTestRootDisposable());
    assertEquals(StandardCharsets.US_ASCII, file.getCharset());
    assertTrue(newEncodingProject.isOpen());
  }

  @Test
  public void testFileInsideJarCorrectlyHandlesBOM() throws IOException {
    Path jar = tempDir.getRootPath().resolve("x.jar");
    String text = "update";
    byte[] bytes = ArrayUtil.mergeArrays(CharsetToolkit.UTF16BE_BOM, text.getBytes(StandardCharsets.UTF_16BE));
    String name = "some_random_name";
    IoTestUtil.createTestJar(jar.toFile(), List.of(Pair.create(name, bytes)));
    VirtualFile vFile = getVirtualFile(jar);
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

  @Test
  public void testBigFileInsideJarCorrectlyHandlesBOM() throws IOException {
    Path jar = tempDir.getRootPath().resolve("x.jar");
    String bigText = StringUtil.repeat("u", FileSizeLimit.getDefaultContentLoadLimit() + 1);
    byte[] utf16beBytes = ArrayUtil.mergeArrays(CharsetToolkit.UTF16BE_BOM, bigText.getBytes(StandardCharsets.UTF_16BE));
    String name = "some_random_name";
    IoTestUtil.createTestJar(jar.toFile(), List.of(Pair.create(name, utf16beBytes)));
    VirtualFile vFile = getVirtualFile(jar);
    VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(vFile);
    assertNotNull(jarRoot);

    VirtualFile file = jarRoot.findChild(name);
    assertNotNull(file);
    try (InputStream stream = file.getInputStream()) {
      String loaded = new String(FileUtil.loadBytes(stream, 8192 * 2), StandardCharsets.UTF_16BE);
      assertEquals(bigText.substring(0, 8192), loaded);
    }
    assertEquals(PlainTextFileType.INSTANCE, file.getFileType());
    assertEquals(StandardCharsets.UTF_16BE, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF16BE_BOM, file.getBOM());
    // should not load the entire file anyway
    //assertEquals(text, LoadTextUtil.loadText(file).toString());
  }

  @Test
  public void testSevenBitFileTextOptimisationWorks() throws IOException {
    VirtualFile virtualFile = createFile("x.txt", "some random\nfile content\n");
    CharSequence loaded = LoadTextUtil.loadText(virtualFile);
    assertTrue(loaded.getClass().getName(), loaded instanceof ByteArrayCharSequence);
  }

  @Test
  public void testUTF16LEWithNoBOMIsAThing() throws IOException {
    String text = "text";
    VirtualFile vFile = createTempFile("txt", NO_BOM, text, StandardCharsets.UTF_16LE);

    vFile.setCharset(StandardCharsets.UTF_16LE);
    vFile.setBOM(null);
    CharSequence loaded = LoadTextUtil.loadText(vFile);
    assertEquals(text, loaded.toString());
  }

  @Test
  public void testNewUTF8FileCanBeCreatedWithOrWithoutBOMDependingOnTheSettings() throws IOException {
    EncodingProjectManagerImpl manager = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject());
    EncodingProjectManagerImpl.BOMForNewUTF8Files old = manager.getBOMForNewUTF8Files();
    String oldProject = manager.getDefaultCharsetName();
    manager.setDefaultCharsetName(CharsetToolkit.UTF8);
    try {
      manager.setBOMForNewUtf8Files(EncodingProjectManagerImpl.BOMForNewUTF8Files.NEVER);
      VirtualFile file = createFile("x.txt", "xx");
      assertNull(file.getBOM());
      @Language("XML")
      String xxTag = "<xx/>";
      // internal files must never be BOMed
      VirtualFile imlFile = createFile("x.iml", xxTag);
      assertNull(imlFile.getBOM());

      manager.setBOMForNewUtf8Files(EncodingProjectManagerImpl.BOMForNewUTF8Files.ALWAYS);
      VirtualFile file2 = createFile("x2.txt", "xx");
      assertArrayEquals(CharsetToolkit.UTF8_BOM, file2.getBOM());
      // internal files must never be BOMed
      imlFile = createFile("x2.iml", xxTag);
      assertNull(imlFile.getBOM());

      manager.setBOMForNewUtf8Files(EncodingProjectManagerImpl.BOMForNewUTF8Files.WINDOWS_ONLY);
      VirtualFile file3 = createFile("x3.txt", "xx");
      byte[] expected = SystemInfo.isWindows ? CharsetToolkit.UTF8_BOM : null;
      assertArrayEquals(expected, file3.getBOM());
      // internal files must never be BOMed
      imlFile = createFile("x3.iml", xxTag);
      assertNull(imlFile.getBOM());

      manager.setBOMForNewUtf8Files(EncodingProjectManagerImpl.BOMForNewUTF8Files.NEVER);
      VirtualFile file4 = createFile("x4.txt", "xx");
      assertNull(file4.getBOM());
      // internal files must never be BOMed
      imlFile = createFile("x4.iml", xxTag);
      assertNull(imlFile.getBOM());
    }
    finally {
      manager.setBOMForNewUtf8Files(old);
      manager.setDefaultCharsetName(oldProject);
    }
  }

  @Test
  public void testBigFileAutoDetectedAsTextMustDetermineItsEncodingFromTheWholeTextToMinimizePossibilityOfUmlautInTheEndMisDetectionError() throws IOException {
    // must not allow sheer luck to have guessed UTF-8 correctly
    EncodingProjectManager.getInstance(getProject()).setDefaultCharsetName(StandardCharsets.US_ASCII.name());
    VirtualFile src = getTestRoot().findChild("BIG_CHANGES");
    assertNotNull(src);

    // create a new file to avoid caching the file AUTO_DETECTED attribute. we need to re-detect it from scratch to test its correctness
    VirtualFile file = createTempFile("blah-blah", NO_BOM, new String(src.contentsToByteArray(), StandardCharsets.UTF_8), StandardCharsets.UTF_8);

    assertNull(file.getBOM());
    assertEquals(PlainTextFileType.INSTANCE, file.getFileType());
    assertEquals(StandardCharsets.UTF_8, file.getCharset());
  }

  @Test
  public void testEncodingReDetectionRequestsOnDocumentChangeAreBatchedToImprovePerformance() throws IOException {
    VirtualFile file = createTempFile("txt", NO_BOM, "xxx", StandardCharsets.US_ASCII);
    Document document = requireNonNull(getDocument(file));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, " "));
    EncodingManagerImpl encodingManager = (EncodingManagerImpl)EncodingManager.getInstance();
    encodingManager.waitAllTasksExecuted();
    Benchmark.newBenchmark("encoding re-detect requests", ()->{
      for (int i = 0; i < 100_000_000; i++) {
        encodingManager.queueUpdateEncodingFromContent(document);
      }
      encodingManager.waitAllTasksExecuted();
      UIUtil.dispatchAllInvocationEvents();
    }).start();
  }

  @Test
  public void testEncodingDetectionRequestsRunAtMostOneThreadForEachDocument() throws Throwable {
    Map<VirtualFile, Thread> detectThreads = new ConcurrentHashMap<>();
    AtomicReference<Throwable> exception = new AtomicReference<>();
    class MyFT extends LanguageFileType implements FileTypeIdentifiableByVirtualFile {
      private MyFT() { super(new com.intellij.lang.Language("my") {}); }
      @Override public boolean isMyFileType(@NotNull VirtualFile file) { return getDefaultExtension().equals(file.getExtension()); }
      @Override public @NotNull String getName() { return "my"; }
      @Override public @NotNull String getDescription() { return getName(); }
      @Override public @NotNull String getDefaultExtension() { return "my"; }
      @Override public Icon getIcon() { return null; }

      @Override
      public Charset extractCharsetFromFileContent(@Nullable Project project, @Nullable VirtualFile file, @NotNull CharSequence content) {
        Thread prev = detectThreads.put(file, Thread.currentThread());
        try {
          if (prev != null) {
            exception.set(new Throwable(file +" has two detection threads: "+prev +" and "+ Thread.currentThread()+"\nThe full thread dump:\n"+ThreadDumper.dumpThreadsToString()));
          }
          TimeoutUtil.sleep(1000);
          return StandardCharsets.US_ASCII;
        }
        finally {
          detectThreads.remove(file);
        }
      }
    }

    MyFT foo = new MyFT();
    ((FileTypeManagerImpl)FileTypeManagerEx.getInstanceEx()).registerFileType(foo, List.of(), getTestRootDisposable(), PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID));

    VirtualFile file = createTempFile("my", NO_BOM, StringUtil.repeat("c", 20), StandardCharsets.US_ASCII);
    FileEditorManager.getInstance(getProject()).openFile(file, false);

    Document document = getDocument(file);
    assertEquals(foo, file.getFileType());
    file.setCharset(null);

    for (int i = 0; i < 1000; i++) {
      WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, " "));
    }

    EncodingManagerImpl encodingManager = (EncodingManagerImpl)EncodingManager.getInstance();
    encodingManager.waitAllTasksExecuted();
    UIUtil.dispatchAllInvocationEvents();

    FileEditorManager.getInstance(getProject()).closeFile(file);
    ThreadingAssertions.assertEventDispatchThread();

    if (exception.get() != null) {
      throw exception.get();
    }
  }

  @Test
  public void testSetMappingMustResetEncodingOfNotYetLoadedFiles() {
    VirtualFile dir = getVirtualFile(tempDir.getRootPath());
    ModuleRootModificationUtil.addContentRoot(getModule(), dir);
    VirtualFile file = HeavyPlatformTestCase.createChildData(dir, "my.txt");
    HeavyPlatformTestCase.setFileText(file, "xxx");
    file.setCharset(StandardCharsets.US_ASCII);
    FileDocumentManager.getInstance().saveAllDocuments();
    UIUtil.dispatchAllInvocationEvents();

    assertNull(FileDocumentManager.getInstance().getCachedDocument(file));
    assertEquals(StandardCharsets.US_ASCII, file.getCharset());

    ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject())).setMapping(Collections.singletonMap(dir, WINDOWS_1251));
    assertEquals(WINDOWS_1251, file.getCharset());
  }

  @Test
  public void testEncodingMappingMustNotContainInvalidFiles() {
    VirtualFile dir = getVirtualFile(tempDir.getRootPath());
    VirtualFile root = dir.getParent();
    ModuleRootModificationUtil.addContentRoot(getModule(), dir);
    FileDocumentManager.getInstance().saveAllDocuments();
    UIUtil.dispatchAllInvocationEvents();

    ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject())).setMapping(Collections.singletonMap(dir, WINDOWS_1251));
    Set<? extends VirtualFile> mappings = ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject())).getAllMappings().keySet();
    assertTrue(mappings.contains(dir));

    VfsTestUtil.deleteFile(dir);
    UIUtil.dispatchAllInvocationEvents();
    assertFalse(dir.isValid());

    mappings = ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject())).getAllMappings().keySet();
    assertFalse(mappings.contains(dir));
    for (VirtualFile mapping : mappings) {
      assertTrue(mapping.isValid());
    }

    dir = HeavyPlatformTestCase.createChildDirectory(root, dir.getName());

    mappings = ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject())).getAllMappings().keySet();
    assertTrue(mappings.contains(dir));
    for (VirtualFile mapping : ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject())).getAllMappings().keySet()) {
      assertTrue(mapping.isValid());
    }
  }

  @Test
  public void testFileEncodingProviderOverridesMapping() throws IOException {
    FileEncodingProvider encodingProvider = new FileEncodingProvider() {
      @Override
      public Charset getEncoding(@NotNull VirtualFile virtualFile, Project project) {
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

  @Test
  public void testForcedCharsetOverridesFileEncodingProvider() throws IOException {
    String ext = "yyy";
    class MyForcedFileType extends LanguageFileType {
      protected MyForcedFileType() { super(new com.intellij.lang.Language("test") {}); }
      @Override public @NotNull String getName() { return "Test"; }
      @Override public @NotNull String getDescription() { return "Test"; }
      @Override public @NotNull String getDefaultExtension() { return ext; }
      @Override public Icon getIcon() { return null; }
      @Override public String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) { return StandardCharsets.ISO_8859_1.name(); }
    }
    MyForcedFileType fileType = new MyForcedFileType();
    FileEncodingProvider encodingProvider = (__, project) -> StandardCharsets.UTF_16;
    FileEncodingProvider.EP_NAME.getPoint().registerExtension(encodingProvider, getTestRootDisposable());
    FileTypeManagerImpl fileTypeManager = (FileTypeManagerImpl)FileTypeManagerEx.getInstanceEx();
    fileTypeManager.registerFileType(fileType, List.of(new ExtensionFileNameMatcher(ext)), getTestRootDisposable(),
                                     PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID));
    VirtualFile file = createTempFile(ext, NO_BOM, "some", StandardCharsets.UTF_8);
    assertEquals(fileType, file.getFileType());
    assertEquals(StandardCharsets.ISO_8859_1, file.getCharset());
  }

  @Test
  public void testDetectedCharsetOverridesFileEncodingProvider() throws IOException {
    FileEncodingProvider encodingProvider = (__, project) -> WINDOWS_1251;
    FileEncodingProvider.EP_NAME.getPoint().registerExtension(encodingProvider, getTestRootDisposable());
    VirtualFile file = createTempFile("yyy", NO_BOM, "Some text" + THREE_RUSSIAN_LETTERS, StandardCharsets.UTF_8);
    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    assertEquals(LoadTextUtil.AutoDetectionReason.FROM_BYTES, LoadTextUtil.getCharsetAutoDetectionReason(file));
  }

  @Test
  public void testEncodingWidgetMustBeAvailableForReadonlyFiles() {
    Project project = getProject();
    @SuppressWarnings("UsagesOfObsoleteApi") EncodingPanel panel = new EncodingPanel(project, ((ComponentManagerEx)project).getCoroutineScope()) {
      @Override
      protected VirtualFile getSelectedFile() {
        LightVirtualFile file = new LightVirtualFile("x.txt", "xxx");
        file.setWritable(false);
        return file;
      }
    };
    panel.updateInTests(true);
    assertTrue(panel.isActionEnabled());
    Disposer.dispose(panel);
  }

  @Test
  public void testWindows1252MustBeDetectedEvenIfItLooksLikeInvalidUtf8() {
    EncodingProjectManager.getInstance(getProject()).setDefaultCharsetName(WINDOWS_1252.name());
    VirtualFile v = find("StartWin1252");
    assertEquals(WINDOWS_1252, v.getCharset());
  }
}
