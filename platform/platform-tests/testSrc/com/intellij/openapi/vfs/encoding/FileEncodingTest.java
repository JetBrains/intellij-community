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
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
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
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
  private static final String SOME_CYRILLIC_LETTERS = "\u043F\u0440\u0438\u0432\u0435\u0442";

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
    if (FileDocumentManager.getInstance() instanceof FileDocumentManagerImpl impl) {
      ApplicationManager.getApplication().runWriteAction(() -> impl.dropAllUnsavedDocuments());
    }
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
    var testRoot = new File(PathManagerEx.getCommunityHomePath(), "platform/platform-tests/testData/vfs/encoding");
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
    var dir = tempDir.newDirectoryPath();
    var vDir = getVirtualFile(dir);
    return WriteAction.compute(() -> {
      if (!ModuleRootManager.getInstance(getModule()).getFileIndex().isInSourceContent(vDir)) {
        PsiTestUtil.addSourceContentToRoots(getModule(), vDir);
      }
      var vFile = vDir.createChildData(vDir, fileName);
      LoadTextUtil.write(getProject(), vFile, this, text, -1);
      assertNotNull(PsiManager.getInstance(getProject()).findFile(vFile));
      return vFile;
    });
  }

  private VirtualFile createTempFile(String ext, byte[] BOM, String content, Charset charset) throws IOException {
    var file = Files.createTempFile(tempDir.getRootPath(), "copy", '.' + ext);
    try (var stream = Files.newOutputStream(file)) {
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
    var vTestRoot = getTestRoot();
    var xml = vTestRoot.findChild("xWin1251.xml");

    var expected = prolog(WINDOWS_1251) + XML_TEST_BODY;
    var text = getDocument(xml).getText();

    assertEquals(expected, text);
  }

  @Test
  public void testXmlProlog() throws IOException {
    var vTestRoot = getTestRoot();
    var xml = requireNonNull(vTestRoot.findChild("xNotepadUtf8.xml"));

    var expected = UTF8_XML_PROLOG + XML_TEST_BODY;
    var text = getDocument(xml).getText();

    if (!expected.equals(text)) {
      System.err.print("expected = ");
      for (var i = 0; i < 50; i++) {
        var c = expected.charAt(i);
        System.err.print(Integer.toHexString(c) + ", ");
      }
      System.err.println();
      System.err.print("expected bytes = ");
      var expectedBytes = FileUtil.loadFileBytes(new File(xml.getPath()));
      for (var i = 0; i < 50; i++) {
        var c = expectedBytes[i];
        System.err.print(Integer.toHexString(c) + ", ");
      }
      System.err.println();

      System.err.print("text = ");
      for (var i = 0; i < 50; i++) {
        var c = text.charAt(i);
        System.err.print(Integer.toHexString(c) + ", ");
      }
      System.err.println();
      System.err.print("text bytes = ");
      var textBytes = xml.contentsToByteArray();
      for (var i = 0; i < 50; i++) {
        var c = textBytes[i];
        System.err.print(Integer.toHexString(c) + ", ");
      }
      System.err.println();
      var charsetFromProlog = XmlCharsetDetector.extractXmlEncodingFromProlog(xml.contentsToByteArray());
      System.err.println("charsetFromProlog = " + charsetFromProlog);
      var charset = xml.getCharset();
      System.err.println("charset = " + charset);
    }
    assertEquals(expected, text);
  }

  @Test
  public void testChangeToUtfProlog() throws IOException {
    var src = find("xWin1251.xml");
    var file = tempDir.getRootPath().resolve("copy.xml");
    Files.copy(src.toNioPath(), file, StandardCopyOption.REPLACE_EXISTING);

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      var xml = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
      var document = getDocument(xml);

      setText(document, UTF8_XML_PROLOG + XML_TEST_BODY);
      FileDocumentManager.getInstance().saveAllDocuments();

      var savedBytes = Files.readAllBytes(file);
      var saved = new String(savedBytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
      var expected = (UTF8_XML_PROLOG + XML_TEST_BODY).replace("\r\n", "\n");

      assertEquals(expected, saved);
    });
  }

  @Test
  public void testDefaultHtml() {
    var file = find("defaultHtml.html");

    assertEquals(EncodingProjectManager.getInstance(getProject()).getDefaultCharset(), file.getCharset());
  }

  @Test
  public void testSevenBitTextMustBeDetectedAsDefaultProjectEncodingInsteadOfUsAscii() throws IOException {
    var file = createTempFile("txt", NO_BOM, "xxx\nxxx", WINDOWS_1251);

    assertEquals(defaultProjectEncoding(), file.getCharset());
  }

  @Test
  public void testTrickyProlog() {
    var xml = find("xTrickyProlog.xml");

    assertEquals("Big5", xml.getCharset().name());
  }

  @Test
  public void testDefaultXml() {
    var xml = find("xDefault.xml");

    assertEquals(StandardCharsets.UTF_8, xml.getCharset());
  }

  @Test
  public void testIbm866() {
    var xml = find("xIbm866.xml");

    var expected = prolog(Charset.forName("IBM866")) + XML_TEST_BODY;
    var text = getDocument(xml).getText();

    assertEquals(expected, text);
  }

  @Test
  public void testUTF16BOM() throws IOException {
    var file = find("UTF16BOM.txt");
    var source = new File(file.getPath());
    var bytesSource = FileUtil.loadFileBytes(source);
    assertTrue(Arrays.toString(bytesSource), CharsetToolkit.hasUTF16LEBom(bytesSource));
    assertEquals("[-1, -2, 31, 4, 64, 4, 56, 4]", Arrays.toString(bytesSource));

    var document = getDocument(file);
    var text = document.getText();
    assertEquals("\u041f\u0440\u0438", text);

    var copy = tempDir.getRootPath().resolve("copy.txt");
    Files.createDirectories(copy.getParent());
    Files.copy(source.toPath(), copy);
    var fileCopy = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(copy);
    document = getDocument(fileCopy);
    //assertTrue(CharsetToolkit.hasUTF16LEBom(fileCopy.getBOM()));

    setText(document, "\u04ab\u04cd\u04ef");
    FileDocumentManager.getInstance().saveAllDocuments();
    var bytes = Files.readAllBytes(copy);
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
    var file = createTempFile("html", NO_BOM, "<html><head>" + metaWithWindowsEncoding + "</head>" + SOME_CYRILLIC_LETTERS + "</html>", WINDOWS_1252);
    assertEquals(WINDOWS_1252, file.getCharset());

    var document = getDocument(file);
    @Language("HTML")
    var text = "<html><head>" + metaWithUtf8Encoding + "</head>" + SOME_CYRILLIC_LETTERS + "</html>";
    setText(document, text);
    FileDocumentManager.getInstance().saveAllDocuments();
    assertEquals(StandardCharsets.UTF_8, file.getCharset());
  }

  @Test
  public void testHtmlEncodingPreferBOM() throws IOException {
    @Language("HTML")
    var content = """
      <html>
      <head><meta http-equiv="Content-Type" content="text/html; charset=windows-1252"></head>
      %s
      </html>""".formatted(SOME_CYRILLIC_LETTERS);
    var file = createTempFile("html", CharsetToolkit.UTF8_BOM, content, WINDOWS_1252);
    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF8_BOM, file.getBOM());
  }

  @Test
  public void testHtmlEncodingMustBeCaseInsensitive() throws IOException {
    @Language("HTML")
    var content = """
      <html>
      <head>
      <meta http-equiv="content-type" content="text/html;charset=us-ascii">\s
      </head>
      <body>
      xyz
      </body>
      </html>""";
    var file = createTempFile("html", NO_BOM, content, WINDOWS_1252);
    assertEquals(StandardCharsets.US_ASCII, file.getCharset());
  }

  @Test
  public void testHtmlContentAttributeOrder() throws IOException {
    @Language("HTML")
    var content = """
      <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
      \t\t"http://www.w3.org/TR/html4/loose.dtd">
      <html> <head>
      \t<meta content="text/html;charset=US-ASCII" http-equiv="Content-Type">
      </head> </html>""";
    var file = createTempFile("html", NO_BOM, content, WINDOWS_1252);
    assertEquals(StandardCharsets.US_ASCII, file.getCharset());
  }

  @Test
  public void testXHtmlStuff() throws IOException {
    @Language("HTML")
    var text = "<xxx>\n</xxx>";
    var file = createTempFile("xhtml", NO_BOM, text, WINDOWS_1252);

    var document = getDocument(file);
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
    var text = "xyz";
    var file = createTempFile("txt", NO_BOM, text, StandardCharsets.UTF_8);
    assertEquals(defaultProjectEncoding(), file.getCharset());
    var ioFile = new File(file.getPath());

    EncodingProjectManager.getInstance(getProject()).setEncoding(file, WINDOWS_1251);
    UIUtil.dispatchAllInvocationEvents();

    var document = getDocument(file);
    var changed = new boolean[]{false};
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
    var bytes = FileUtil.loadFileBytes(ioFile);
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

    // after 'save as US_ASCII', the file is changed
    bytes = FileUtil.loadFileBytes(ioFile);
    assertArrayEquals(text.getBytes(StandardCharsets.US_ASCII), bytes);
  }

  @Test
  public void testCopyMove() throws IOException {
    var vDir1 = getVirtualFile(tempDir.newDirectoryPath("dir1"));
    var vDir2 = getVirtualFile(tempDir.newDirectoryPath("dir2"));

    EncodingProjectManager.getInstance(getProject()).setEncoding(vDir1, WINDOWS_1251);
    EncodingProjectManager.getInstance(getProject()).setEncoding(vDir2, StandardCharsets.US_ASCII);
    UIUtil.dispatchAllInvocationEvents();

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      var xxx = vDir1.createChildData(this, "xxx.txt");
      HeavyPlatformTestCase.setFileText(xxx, SOME_CYRILLIC_LETTERS);
      assertEquals(WINDOWS_1251, xxx.getCharset());
      var copied = xxx.copy(this, vDir2, "xxx2.txt");
      assertEquals(WINDOWS_1251, copied.getCharset());

      var xus = vDir2.createChildData(this, "xxx-us.txt");
      HeavyPlatformTestCase.setFileText(xus, SOME_CYRILLIC_LETTERS);
      assertEquals(StandardCharsets.US_ASCII, xus.getCharset());

      xus.move(this, vDir1);
      assertEquals(StandardCharsets.US_ASCII, xus.getCharset());
    });
  }

  @Test
  public void testCopyNested() throws IOException {
    var vDir1 = getVirtualFile(tempDir.newDirectoryPath("dir1"));
    var vDir2 = getVirtualFile(tempDir.newDirectoryPath("dir2"));

    EncodingProjectManager.getInstance(getProject()).setEncoding(vDir1, WINDOWS_1251);
    EncodingProjectManager.getInstance(getProject()).setEncoding(vDir2, StandardCharsets.US_ASCII);
    UIUtil.dispatchAllInvocationEvents();

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      var winF = vDir1.createChildData(this, "xxx.txt");

      var psiDir1 = requireNonNull(PsiManager.getInstance(getProject()).findDirectory(vDir1));
      var psiDir2 = requireNonNull(PsiManager.getInstance(getProject()).findDirectory(vDir2));
      CopyFilesOrDirectoriesHandler.copyToDirectory(psiDir1, psiDir1.getName(), psiDir2);
      var winFCopy = requireNonNull(psiDir2.getVirtualFile().findFileByRelativePath(psiDir1.getName() + "/" + winF.getName()));
      assertEquals(WINDOWS_1251, winFCopy.getCharset());
      var dir1Copy = psiDir2.getVirtualFile().findChild(psiDir1.getName());
      assertEquals(WINDOWS_1251, EncodingProjectManager.getInstance(getProject()).getEncoding(dir1Copy, false));
      assertNull(EncodingProjectManager.getInstance(getProject()).getEncoding(winFCopy, false));
    });
  }

  @Test
  public void testBOMPersistsAfterEditing() throws IOException {
    var file = createTempFile("txt", CharsetToolkit.UTF8_BOM, XML_TEST_BODY, StandardCharsets.UTF_8);

    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF8_BOM, file.getBOM());

    var document = getDocument(file);
    setText(document, "horseradish");
    FileDocumentManager.getInstance().saveAllDocuments();

    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF8_BOM, file.getBOM());

    var bytes = FileUtil.loadFileBytes(VfsUtilCore.virtualToIoFile(file));
    assertTrue(CharsetToolkit.hasUTF8Bom(bytes));
  }

  @Test
  public void testBOMPersistsAfterIndexRescan() throws IOException {
    var file = createTempFile("txt", CharsetToolkit.UTF8_BOM, XML_TEST_BODY, StandardCharsets.UTF_8);

    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF8_BOM, file.getBOM());

    var document = getDocument(file);
    var newContent = "horseradish";
    setText(document, newContent);

    FileDocumentManager.getInstance().saveAllDocuments();

    var bytes = FileUtil.loadFileBytes(VfsUtilCore.virtualToIoFile(file));
    var charset = LoadTextUtil.detectCharsetAndSetBOM(file, bytes, file.getFileType());
    assertEquals(StandardCharsets.UTF_8, charset);
    assertArrayEquals(CharsetToolkit.UTF8_BOM, file.getBOM());

    assertTrue(CharsetToolkit.hasUTF8Bom(bytes));
  }

  @Test
  public void testVFileGetInputStreamIsBOMAware() throws IOException {
    var file = find("UTF16BOM.txt");

    var vfsLoad = VfsUtilCore.loadText(file);
    assertEquals("\u041f\u0440\u0438", vfsLoad);

    var ltuText = LoadTextUtil.loadText(file);
    assertEquals(vfsLoad, ltuText.toString());

    assertArrayEquals(CharsetToolkit.UTF16LE_BOM, file.getBOM());
  }
  @Test
  public void testSetCharsetAfter() throws IOException {
    var file = find("UTF16LE_NO_BOM.txt");
    file.setCharset(StandardCharsets.UTF_16LE);
    file.setBOM(null);
    var vfsLoad = VfsUtilCore.loadText(file);
    assertEquals("\u041f\u0440\u0438\u0432\u0435\u0442", vfsLoad);
    assertNull(file.getBOM());
  }

  @Test
  public void testBOMResetAfterChangingUtf16ToUtf8() throws IOException {
    var file = createTempFile("txt", CharsetToolkit.UTF16BE_BOM, SOME_CYRILLIC_LETTERS, StandardCharsets.UTF_16BE);

    assertEquals(StandardCharsets.UTF_16BE, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF16BE_BOM, file.getBOM());

    var document = getDocument(file);
    var newContent = "horseradish";
    setText(document, newContent);
    FileDocumentManager.getInstance().saveAllDocuments();
    assertEquals(StandardCharsets.UTF_16BE, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF16BE_BOM, file.getBOM());

    EncodingUtil.saveIn(getProject(), document, null, file, StandardCharsets.UTF_8);

    var bytes = FileUtil.loadFileBytes(VfsUtilCore.virtualToIoFile(file));

    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    assertNull(file.getBOM());

    assertFalse(CharsetToolkit.hasUTF8Bom(bytes));
  }

  @Test
  public void testConvertNotAvailableForHtml() throws IOException {
    @org.intellij.lang.annotations.Language("HTML")
    var content = "<html><head><meta content=\"text/html; charset=utf-8\" http-equiv=\"content-type\"></head>" +
                  "<body>" + SOME_CYRILLIC_LETTERS +
                  "</body></html>";
    var virtualFile = createTempFile("html", NO_BOM, content, StandardCharsets.UTF_8);
    var document = FileDocumentManager.getInstance().getDocument(virtualFile);
    assertNotNull(document);
    FileDocumentManager.getInstance().saveAllDocuments();
    var fileType = virtualFile.getFileType();
    assertEquals(FileTypeManager.getInstance().getStdFileType("HTML"), fileType);
    var fromType = ((LanguageFileType)fileType).extractCharsetFromFileContent(getProject(), virtualFile, (CharSequence)content);
    assertEquals(StandardCharsets.UTF_8, fromType);
    var fromProlog = XmlCharsetDetector.extractXmlEncodingFromProlog(content);
    assertNull(fromProlog);
    var charsetFromContent = EncodingManagerImpl.computeCharsetFromContent(virtualFile);
    var docFromVF = FileDocumentManager.getInstance().getDocument(virtualFile);
    var extractedCharset = ((LanguageFileType)fileType).extractCharsetFromFileContent(getProject(), virtualFile, document.getImmutableCharSequence());
    assertEquals("docFromVF: " + docFromVF + "; fileType=" + fileType + "; extractedCharset=" + extractedCharset, StandardCharsets.UTF_8, charsetFromContent);
    var result = EncodingUtil.checkCanConvert(virtualFile);
    assertEquals(EncodingUtil.FailReason.BY_FILE, result);
  }

  @Test
  public void testConvertReload() throws IOException {
    var file = createTempFile("txt", CharsetToolkit.UTF16BE_BOM, SOME_CYRILLIC_LETTERS, StandardCharsets.UTF_16BE);

    assertEquals(StandardCharsets.UTF_16BE, file.getCharset());
    assertArrayEquals(CharsetToolkit.UTF16BE_BOM, file.getBOM());

    var document = getDocument(file);

    var bytes = file.contentsToByteArray();
    var text = document.getText();
    assertNotSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, WINDOWS_1251));
    assertSame(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(file, text, bytes, StandardCharsets.US_ASCII));
    var result = EncodingUtil.checkCanReload(file, null);
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

    var fc = FileUtil.createTempDirectory("", "");
    var root = requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fc));
    PsiTestUtil.addContentRoot(getModule(), root);

    var file = HeavyPlatformTestCase.createChildData(root, "win.txt");
    Files.writeString(file.toNioPath(), SOME_CYRILLIC_LETTERS, WINDOWS_1251);
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
    var file = createTempFile("txt", NO_BOM, SOME_CYRILLIC_LETTERS, StandardCharsets.UTF_8);
    getDocument(file);

    assertEquals(LoadTextUtil.AutoDetectionReason.FROM_BYTES, LoadTextUtil.getCharsetAutoDetectionReason(file));

    Files.writeString(file.toNioPath(), SOME_CYRILLIC_LETTERS, WINDOWS_1251);
    file.refresh(false, false);

    assertNull(LoadTextUtil.getCharsetAutoDetectionReason(file));
  }

  @Test
  public void testSafeToConvert() throws IOException {
    var text = "xxx";
    var file = createTempFile("txt", NO_BOM, text, StandardCharsets.UTF_8);
    var bytes = file.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.ABSOLUTELY, EncodingUtil.isSafeToConvertTo(file, text, bytes, StandardCharsets.US_ASCII));
    assertEquals(EncodingUtil.Magic8.ABSOLUTELY, EncodingUtil.isSafeToConvertTo(file, text, bytes, WINDOWS_1251));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(file, text, bytes, StandardCharsets.UTF_16BE));

    var cyrText = SOME_CYRILLIC_LETTERS;
    var cyrFile = createTempFile("txt", NO_BOM, cyrText, StandardCharsets.UTF_8);
    var cyrBytes = cyrFile.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(cyrFile, cyrText, cyrBytes, StandardCharsets.US_ASCII));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(cyrFile, cyrText, cyrBytes, WINDOWS_1251));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(cyrFile, cyrText, cyrBytes, StandardCharsets.UTF_16BE));

    var bomText = SOME_CYRILLIC_LETTERS;
    var bomFile = createTempFile("txt", CharsetToolkit.UTF16LE_BOM, bomText, StandardCharsets.UTF_16LE);
    var bomBytes = bomFile.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToConvertTo(bomFile, bomText, bomBytes, StandardCharsets.US_ASCII));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(bomFile, bomText, bomBytes, WINDOWS_1251));
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToConvertTo(bomFile, bomText, bomBytes, StandardCharsets.UTF_16BE));
  }

  @Test
  public void testSafeToReloadUtf8Bom() throws IOException {
    var text = SOME_CYRILLIC_LETTERS;
    var file = createTempFile("txt", CharsetToolkit.UTF8_BOM, text, StandardCharsets.UTF_8);
    var bytes = file.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, StandardCharsets.US_ASCII));
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, StandardCharsets.UTF_16LE));
  }

  @Test
  public void testSafeToReloadUtf8NoBom() throws IOException {
    var text = SOME_CYRILLIC_LETTERS;
    var file = createTempFile("txt", NO_BOM, text, StandardCharsets.UTF_8);
    var bytes = file.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, StandardCharsets.US_ASCII));
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, StandardCharsets.UTF_16LE));
  }

  @Test
  public void testSafeToReloadText() throws IOException {
    var text = "xxx";
    var file = createTempFile("txt", NO_BOM, text, StandardCharsets.US_ASCII);
    var bytes = file.contentsToByteArray();
    assertEquals(EncodingUtil.Magic8.ABSOLUTELY, EncodingUtil.isSafeToReloadIn(file, text, bytes, WINDOWS_1251));
    assertEquals(EncodingUtil.Magic8.ABSOLUTELY, EncodingUtil.isSafeToReloadIn(file, text, bytes, StandardCharsets.UTF_8));
    assertEquals(EncodingUtil.Magic8.NO_WAY, EncodingUtil.isSafeToReloadIn(file, text, bytes, StandardCharsets.UTF_16LE));
  }

  @Test
  public void testMustBeSafeToReloadISO8859TextMistakenlyLoadedInUTF8() throws IOException {
    @SuppressWarnings("SpellCheckingInspection") var isoText = "No se ha encontrado ningún puesto con ese criterio de búsqueda";
    var file = createTempFile("txt", NO_BOM, isoText, StandardCharsets.ISO_8859_1);
    var bytes = isoText.getBytes(StandardCharsets.ISO_8859_1);
    file.setCharset(StandardCharsets.UTF_8);
    var utfText = new String(bytes, StandardCharsets.UTF_8);
    assertEquals(EncodingUtil.Magic8.WELL_IF_YOU_INSIST, EncodingUtil.isSafeToReloadIn(file, utfText, bytes, StandardCharsets.ISO_8859_1));
  }

  @Test
  public void testUndoChangeEncoding() throws IOException {
    var file = createTempFile("txt", NO_BOM, "xxx", StandardCharsets.UTF_8);
    file.setCharset(StandardCharsets.UTF_8);
    UIUtil.dispatchAllInvocationEvents();
    assertEquals(StandardCharsets.UTF_8, file.getCharset());

    var documentManager = FileDocumentManager.getInstance();
    assertNull(documentManager.getCachedDocument(file));

    change(file, WINDOWS_1251);
    assertEquals(WINDOWS_1251, file.getCharset());

    var document = requireNonNull(documentManager.getDocument(file));
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
    var myManager = UndoManager.getInstance(getProject());
    assertTrue("undo is not available", myManager.isUndoAvailable(null));
    myManager.undo(null);
  }

  @Test
  public void testMustBeAbleForFileAccidentallyLoadedInUTF16ToReloadBackToUtf8() throws IOException {
    var file = createTempFile("txt", NO_BOM, "xxx", StandardCharsets.UTF_8);
    file.setCharset(StandardCharsets.UTF_8);
    UIUtil.dispatchAllInvocationEvents();
    var document = FileDocumentManager.getInstance().getDocument(file);
    var oldText = document.getText();
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
    var file = createTempFile("txt", CharsetToolkit.UTF8_BOM, SOME_CYRILLIC_LETTERS, StandardCharsets.UTF_8);
    file.contentsToByteArray();
    var document = requireNonNull(FileDocumentManager.getInstance().getDocument(file));
    assertEquals(SOME_CYRILLIC_LETTERS, document.getText());
    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    EncodingProjectManager.getInstance(getProject()).setEncoding(file, StandardCharsets.UTF_16LE);
    UIUtil.dispatchAllInvocationEvents();

    assertEquals(StandardCharsets.UTF_8, file.getCharset());
  }

  @Test
  public void testExternalChangeStrippedBOM() throws IOException {
    var text = "text";
    var file = createTempFile("txt", CharsetToolkit.UTF8_BOM, text, StandardCharsets.UTF_8);
    file.contentsToByteArray();
    var document = requireNonNull(FileDocumentManager.getInstance().getDocument(file));

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
      var file = createFile("x.txt", "xx");
      assertEquals(WINDOWS_1251, file.getCharset());
    });
  }

  @Test
  public void testNewFileCreatedInProjectEncodingEvenIfItSetToDefault() throws IOException {
    var newCS = Charset.defaultCharset().name().equals("UTF-8") ? WINDOWS_1251 : StandardCharsets.UTF_8;
    EditorTestUtil.saveEncodingsIn(getProject(), newCS, null, () -> {
      var file = createFile("x.txt", "xx");
      assertEquals(EncodingProjectManager.getInstance(getProject()).getDefaultCharset(), file.getCharset());
    });
  }

  @Test
  public void testTheDefaultProjectEncodingIfNotSpecifiedShouldBeIDEEncoding() throws Exception {
    var differentFromDefault = Charset.defaultCharset().equals(WINDOWS_1252) ? WINDOWS_1251 : WINDOWS_1252;
    var oldIDE = EncodingManager.getInstance().getDefaultCharsetName();
    try {
      EncodingManager.getInstance().setDefaultCharsetName(differentFromDefault.name());

      var temp = tempDir.newDirectoryPath();
      var tempDir = getVirtualFile(temp);

      var newProject = ProjectManagerEx.getInstanceEx().openProject(temp, OpenProjectTaskBuilderKt.createTestOpenProjectOptions());
      if (ApplicationManager.getApplication().isDispatchThread()) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      }
      try {
        PlatformTestUtil.saveProject(newProject);

        var newProjectEncoding = EncodingProjectManager.getInstance(newProject).getDefaultCharset();
        assertEquals(differentFromDefault, newProjectEncoding);

        var vFile = WriteCommandAction.runWriteCommandAction(newProject, (ThrowableComputable<VirtualFile, IOException>)() -> {
          var newModule = ModuleManager.getInstance(newProject).newModule(temp, "blah");
          ModuleRootModificationUtil.addContentRoot(newModule, tempDir);
          var v = tempDir.createChildData(this, "x.txt");
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
    var dir = find("newEncoding");
    var file = requireNonNull(dir.findFileByRelativePath("src/xxx.txt"));

    var document = requireNonNull(FileDocumentManager.getInstance().getDocument(file));
    assertNotNull(document.getText());
    UIUtil.dispatchAllInvocationEvents();

    var newEncodingProject = PlatformTestUtil.loadAndOpenProject(dir.toNioPath(), getTestRootDisposable());
    assertEquals(StandardCharsets.US_ASCII, file.getCharset());
    assertTrue(newEncodingProject.isOpen());
  }

  @Test
  public void testFileInsideJarCorrectlyHandlesBOM() throws IOException {
    var jar = tempDir.getRootPath().resolve("x.jar");
    var text = "update";
    var bytes = ArrayUtil.mergeArrays(CharsetToolkit.UTF16BE_BOM, text.getBytes(StandardCharsets.UTF_16BE));
    var name = "some_random_name";
    IoTestUtil.createTestJar(jar.toFile(), List.of(Pair.create(name, bytes)));
    var vFile = getVirtualFile(jar);
    var jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(vFile);
    assertNotNull(jarRoot);

    var file = jarRoot.findChild(name);
    assertNotNull(file);
    assertEquals(StandardCharsets.UTF_16BE, file.getCharset());
    assertEquals(PlainTextFileType.INSTANCE, file.getFileType());
    assertEquals(text, LoadTextUtil.loadText(file).toString());
    try (var stream = file.getInputStream()) {
      var loaded = new String(FileUtil.loadBytes(stream), StandardCharsets.UTF_16BE);
      assertEquals(text, loaded);
    }
  }

  @Test
  public void testBigFileInsideJarCorrectlyHandlesBOM() throws IOException {
    var jar = tempDir.getRootPath().resolve("x.jar");
    var bigText = StringUtil.repeat("u", FileSizeLimit.getDefaultContentLoadLimit() + 1);
    var utf16beBytes = ArrayUtil.mergeArrays(CharsetToolkit.UTF16BE_BOM, bigText.getBytes(StandardCharsets.UTF_16BE));
    var name = "some_random_name";
    IoTestUtil.createTestJar(jar.toFile(), List.of(Pair.create(name, utf16beBytes)));
    var vFile = getVirtualFile(jar);
    var jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(vFile);
    assertNotNull(jarRoot);

    var file = jarRoot.findChild(name);
    assertNotNull(file);
    try (var stream = file.getInputStream()) {
      var loaded = new String(FileUtil.loadBytes(stream, 8192 * 2), StandardCharsets.UTF_16BE);
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
    var virtualFile = createFile("x.txt", "some random\nfile content\n");
    var loaded = LoadTextUtil.loadText(virtualFile);
    assertTrue(loaded.getClass().getName(), loaded instanceof ByteArrayCharSequence);
  }

  @Test
  public void testUTF16LEWithNoBOMIsAThing() throws IOException {
    var text = "text";
    var vFile = createTempFile("txt", NO_BOM, text, StandardCharsets.UTF_16LE);

    vFile.setCharset(StandardCharsets.UTF_16LE);
    vFile.setBOM(null);
    var loaded = LoadTextUtil.loadText(vFile);
    assertEquals(text, loaded.toString());
  }

  @Test
  public void testNewUTF8FileCanBeCreatedWithOrWithoutBOMDependingOnTheSettings() throws IOException {
    var manager = (EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject());
    var old = manager.getBOMForNewUTF8Files();
    var oldProject = manager.getDefaultCharsetName();
    manager.setDefaultCharsetName(CharsetToolkit.UTF8);
    try {
      manager.setBOMForNewUtf8Files(EncodingProjectManagerImpl.BOMForNewUTF8Files.NEVER);
      var file = createFile("x.txt", "xx");
      assertNull(file.getBOM());
      @Language("XML")
      var xxTag = "<xx/>";
      // internal files must never be BOMed
      var imlFile = createFile("x.iml", xxTag);
      assertNull(imlFile.getBOM());

      manager.setBOMForNewUtf8Files(EncodingProjectManagerImpl.BOMForNewUTF8Files.ALWAYS);
      var file2 = createFile("x2.txt", "xx");
      assertArrayEquals(CharsetToolkit.UTF8_BOM, file2.getBOM());
      // internal files must never be BOMed
      imlFile = createFile("x2.iml", xxTag);
      assertNull(imlFile.getBOM());

      manager.setBOMForNewUtf8Files(EncodingProjectManagerImpl.BOMForNewUTF8Files.WINDOWS_ONLY);
      var file3 = createFile("x3.txt", "xx");
      var expected = SystemInfo.isWindows ? CharsetToolkit.UTF8_BOM : null;
      assertArrayEquals(expected, file3.getBOM());
      // internal files must never be BOMed
      imlFile = createFile("x3.iml", xxTag);
      assertNull(imlFile.getBOM());

      manager.setBOMForNewUtf8Files(EncodingProjectManagerImpl.BOMForNewUTF8Files.NEVER);
      var file4 = createFile("x4.txt", "xx");
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
    var src = getTestRoot().findChild("BIG_CHANGES");
    assertNotNull(src);

    // create a new file to avoid caching the file AUTO_DETECTED attribute. we need to re-detect it from scratch to test its correctness
    var file = createTempFile("blah-blah", NO_BOM, new String(src.contentsToByteArray(), StandardCharsets.UTF_8), StandardCharsets.UTF_8);

    assertNull(file.getBOM());
    assertEquals(PlainTextFileType.INSTANCE, file.getFileType());
    assertEquals(StandardCharsets.UTF_8, file.getCharset());
  }

  @Test
  public void testEncodingReDetectionRequestsOnDocumentChangeAreBatchedToImprovePerformance() throws IOException {
    var file = createTempFile("txt", NO_BOM, "xxx", StandardCharsets.US_ASCII);
    var document = requireNonNull(getDocument(file));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, " "));
    var encodingManager = (EncodingManagerImpl)EncodingManager.getInstance();
    encodingManager.waitAllTasksExecuted();
    Benchmark.newBenchmark("encoding re-detect requests", ()->{
      for (var i = 0; i < 100_000_000; i++) {
        encodingManager.queueUpdateEncodingFromContent(document);
      }
      encodingManager.waitAllTasksExecuted();
      UIUtil.dispatchAllInvocationEvents();
    }).start();
  }

  @Test
  public void testEncodingDetectionRequestsRunAtMostOneThreadForEachDocument() throws Throwable {
    var detectThreads = new ConcurrentHashMap<VirtualFile, Thread>();
    var exception = new AtomicReference<Throwable>();
    class MyFT extends LanguageFileType implements FileTypeIdentifiableByVirtualFile {
      private MyFT() { super(new com.intellij.lang.Language("my") {}); }
      @Override public boolean isMyFileType(@NotNull VirtualFile file) { return getDefaultExtension().equals(file.getExtension()); }
      @Override public @NotNull String getName() { return "my"; }
      @Override public @NotNull String getDescription() { return getName(); }
      @Override public @NotNull String getDefaultExtension() { return "my"; }
      @Override public Icon getIcon() { return null; }

      @Override
      public Charset extractCharsetFromFileContent(@Nullable Project project, @Nullable VirtualFile file, @NotNull CharSequence content) {
        var prev = detectThreads.put(file, Thread.currentThread());
        try {
          if (prev != null) {
            exception.set(new Throwable(
              file + " has two detection threads: " + prev + " and " + Thread.currentThread() +
              "\nThe full thread dump:\n" + ThreadDumper.dumpThreadsToString())
            );
          }
          TimeoutUtil.sleep(1000);
          return StandardCharsets.US_ASCII;
        }
        finally {
          detectThreads.remove(file);
        }
      }
    }

    var foo = new MyFT();
    ((FileTypeManagerImpl)FileTypeManagerEx.getInstanceEx()).registerFileType(
      foo, List.of(), getTestRootDisposable(), PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)
    );

    var file = createTempFile("my", NO_BOM, StringUtil.repeat("c", 20), StandardCharsets.US_ASCII);
    FileEditorManager.getInstance(getProject()).openFile(file, false);

    var document = getDocument(file);
    assertEquals(foo, file.getFileType());
    file.setCharset(null);

    for (var i = 0; i < 1000; i++) {
      WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, " "));
    }

    var encodingManager = (EncodingManagerImpl)EncodingManager.getInstance();
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
    var dir = getVirtualFile(tempDir.getRootPath());
    ModuleRootModificationUtil.addContentRoot(getModule(), dir);
    var file = HeavyPlatformTestCase.createChildData(dir, "my.txt");
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
    var dir = getVirtualFile(tempDir.getRootPath());
    var root = dir.getParent();
    ModuleRootModificationUtil.addContentRoot(getModule(), dir);
    FileDocumentManager.getInstance().saveAllDocuments();
    UIUtil.dispatchAllInvocationEvents();

    ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject())).setMapping(Collections.singletonMap(dir, WINDOWS_1251));
    var mappings = ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject())).getAllMappings().keySet();
    assertTrue(mappings.contains(dir));

    VfsTestUtil.deleteFile(dir);
    UIUtil.dispatchAllInvocationEvents();
    assertFalse(dir.isValid());

    mappings = ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject())).getAllMappings().keySet();
    assertFalse(mappings.contains(dir));
    for (var mapping : mappings) {
      assertTrue(mapping.isValid());
    }

    dir = HeavyPlatformTestCase.createChildDirectory(root, dir.getName());

    mappings = ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject())).getAllMappings().keySet();
    assertTrue(mappings.contains(dir));
    for (var mapping : ((EncodingProjectManagerImpl)EncodingProjectManager.getInstance(getProject())).getAllMappings().keySet()) {
      assertTrue(mapping.isValid());
    }
  }

  @Test
  public void testFileEncodingProviderOverridesMapping() throws IOException {
    var encodingProvider = new FileEncodingProvider() {
      @Override
      public Charset getEncoding(@NotNull VirtualFile virtualFile, Project project) {
        return StandardCharsets.UTF_16;
      }
    };
    FileEncodingProvider.EP_NAME.getPoint().registerExtension(encodingProvider, getTestRootDisposable());
    var file = createTempFile("txt", NO_BOM, "some", StandardCharsets.UTF_8);
    var encodingManager = EncodingProjectManager.getInstance(getProject());
    encodingManager.setEncoding(file, StandardCharsets.ISO_8859_1);
    try {
      var charset = encodingManager.getEncoding(file, true);
      assertEquals(StandardCharsets.UTF_16, charset);
    }
    finally {
      encodingManager.setEncoding(file, null);
    }
  }

  @Test
  public void testForcedCharsetOverridesFileEncodingProvider() throws IOException {
    var ext = "yyy";
    class MyForcedFileType extends LanguageFileType {
      protected MyForcedFileType() { super(new com.intellij.lang.Language("test") {}); }
      @Override public @NotNull String getName() { return "Test"; }
      @Override public @NotNull String getDescription() { return "Test"; }
      @Override public @NotNull String getDefaultExtension() { return ext; }
      @Override public Icon getIcon() { return null; }
      @Override public String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) { return StandardCharsets.ISO_8859_1.name(); }
    }
    var fileType = new MyForcedFileType();
    FileEncodingProvider encodingProvider = (__, project) -> StandardCharsets.UTF_16;
    FileEncodingProvider.EP_NAME.getPoint().registerExtension(encodingProvider, getTestRootDisposable());
    var fileTypeManager = (FileTypeManagerImpl)FileTypeManagerEx.getInstanceEx();
    fileTypeManager.registerFileType(fileType, List.of(new ExtensionFileNameMatcher(ext)), getTestRootDisposable(),
                                     PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID));
    var file = createTempFile(ext, NO_BOM, "some", StandardCharsets.UTF_8);
    assertEquals(fileType, file.getFileType());
    assertEquals(StandardCharsets.ISO_8859_1, file.getCharset());
  }

  @Test
  public void testDetectedCharsetOverridesFileEncodingProvider() throws IOException {
    FileEncodingProvider encodingProvider = (__, project) -> WINDOWS_1251;
    FileEncodingProvider.EP_NAME.getPoint().registerExtension(encodingProvider, getTestRootDisposable());
    var file = createTempFile("yyy", NO_BOM, "Some text" + SOME_CYRILLIC_LETTERS, StandardCharsets.UTF_8);
    assertEquals(StandardCharsets.UTF_8, file.getCharset());
    assertEquals(LoadTextUtil.AutoDetectionReason.FROM_BYTES, LoadTextUtil.getCharsetAutoDetectionReason(file));
  }

  @Test
  public void testEncodingWidgetMustBeAvailableForReadonlyFiles() {
    var project = getProject();
    @SuppressWarnings("UsagesOfObsoleteApi") var panel = new EncodingPanel(project, ((ComponentManagerEx)project).getCoroutineScope()) {
      @Override
      protected VirtualFile getSelectedFile() {
        var file = new LightVirtualFile("x.txt", "xxx");
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
    var v = find("StartWin1252");
    assertEquals(WINDOWS_1252, v.getCharset());
  }
}
