// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.file.exclude;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class OverrideFileTypeManagerTest extends BasePlatformTestCase {
  public void testMarkAsPlainText() {
    OverrideFileTypeManager manager = OverrideFileTypeManager.getInstance();
    VirtualFile xml = myFixture.getTempDirFixture().createFile("test.xml");
    FileType originalType = xml.getFileType();
    assertEquals(StdFileTypes.XML, originalType);
    manager.addFile(xml, PlainTextFileType.INSTANCE);
    UIUtil.dispatchAllInvocationEvents(); // reparseFiles in invokeLater
    assertEquals(PlainTextFileType.INSTANCE, xml.getFileType());
    assertTrue(FileTypeManager.getInstance().isFileOfType(xml, PlainTextFileType.INSTANCE));
    assertFalse(FileTypeManager.getInstance().isFileOfType(xml, StdFileTypes.XML));
    manager.removeFile(xml);
    UIUtil.dispatchAllInvocationEvents(); // reparseFiles in invokeLater
    FileType revertedType = xml.getFileType();
    assertEquals(originalType, revertedType);

    manager.addFile(xml, ArchiveFileType.INSTANCE);
    UIUtil.dispatchAllInvocationEvents(); // reparseFiles in invokeLater
    assertEquals(ArchiveFileType.INSTANCE, xml.getFileType());
    manager.removeFile(xml);
    UIUtil.dispatchAllInvocationEvents(); // reparseFiles in invokeLater
    assertEquals(originalType, xml.getFileType());
  }

  public void testLightFileHasNoOverride() {
    var manager = OverrideFileTypeManager.getInstance();
    var xml = myFixture.getTempDirFixture().createFile("test.xml");
    manager.addFile(xml, ArchiveFileType.INSTANCE);
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); // reparseFiles in invokeLater
    try {
      var lightFile = new LightVirtualFile("test.xml", PlainTextFileType.INSTANCE, "");
      assertEquals(PlainTextFileType.INSTANCE, lightFile.getFileType());
      assertNull(manager.getFileValue(new LightVirtualFile("test.xml")));
    }
    finally {
      manager.removeFile(xml);
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); // reparseFiles in invokeLater
    }
  }

  public void testLoadedOverrideAppliesToCachedFile() {
    var manager = emptyManager();
    var xml = myFixture.getTempDirFixture().createFile("test.xml");
    try {
      manager.loadState(state(xml.getUrl(), ArchiveFileType.INSTANCE.getName()));
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); // reparseFiles in invokeLater
      assertEquals(ArchiveFileType.INSTANCE, xml.getFileType());
      assertEquals(ArchiveFileType.INSTANCE.getName(), manager.getFileValue(xml));
    }
    finally {
      reset(manager);
    }
  }

  public void testOverrideLoadedBeforeFileCreationApplies() throws IOException {
    var manager = emptyManager();
    var dir = myFixture.getTempDirFixture().findOrCreateDir("dir");
    try {
      manager.loadState(state(dir.getUrl() + "/test.xml", ArchiveFileType.INSTANCE.getName()));
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); // reparseFiles in invokeLater
      var xml = myFixture.getTempDirFixture().createFile("dir/test.xml");
      assertEquals(ArchiveFileType.INSTANCE, xml.getFileType());
      assertEquals(ArchiveFileType.INSTANCE.getName(), manager.getFileValue(xml));
    }
    finally {
      reset(manager);
    }
  }

  public void testUnresolvedEntrySurvivesSave() throws IOException {
    var manager = emptyManager();
    var url = myFixture.getTempDirFixture().findOrCreateDir("dir").getUrl() + "/missing/test.xml";
    try {
      manager.loadState(state(url, ArchiveFileType.INSTANCE.getName()));
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); // reparseFiles in invokeLater
      var file = assertOneElement(manager.getState().getChildren("file"));
      assertEquals(url, file.getAttributeValue("url"));
      assertEquals(ArchiveFileType.INSTANCE.getName(), file.getAttributeValue("value"));
    }
    finally {
      reset(manager);
    }
  }

  public void testStateWritesFileUrl() {
    var manager = emptyManager();
    var xml = myFixture.getTempDirFixture().createFile("test.xml");
    try {
      manager.addFile(xml, ArchiveFileType.INSTANCE);
      var file = assertOneElement(manager.getState().getChildren("file"));
      assertEquals(xml.getUrl(), file.getAttributeValue("url"));
      assertEquals(ArchiveFileType.INSTANCE.getName(), file.getAttributeValue("value"));

      manager.addFile(xml, PlainTextFileType.INSTANCE);
      file = assertOneElement(manager.getState().getChildren("file"));
      assertEquals(xml.getUrl(), file.getAttributeValue("url"));
      assertNull(file.getAttribute("value"));
    }
    finally {
      reset(manager);
    }
  }

  public void testRemoveDropsUnresolvedEntry() throws IOException {
    var manager = emptyManager();
    var dir = myFixture.getTempDirFixture().findOrCreateDir("dir");
    try {
      manager.loadState(state(dir.getUrl() + "/test.xml", ArchiveFileType.INSTANCE.getName()));
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); // reparseFiles in invokeLater
      var xml = myFixture.getTempDirFixture().createFile("dir/test.xml");
      assertTrue(manager.removeFile(xml));
      assertEmpty(manager.getState().getChildren("file"));
      assertNull(manager.getFileValue(xml));
    }
    finally {
      reset(manager);
    }
  }

  public void testReloadWithChangedValueReparses() {
    var manager = emptyManager();
    var xml = myFixture.getTempDirFixture().createFile("test.xml");
    try {
      manager.addFile(xml, ArchiveFileType.INSTANCE);
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); // reparseFiles in invokeLater
      assertEquals(ArchiveFileType.INSTANCE, xml.getFileType());
      var psiManager = PsiManager.getInstance(getProject());
      var binaryPsi = psiManager.findFile(xml);
      assertInstanceOf(binaryPsi, PsiBinaryFile.class);

      manager.loadState(state(xml.getUrl(), null));
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); // reparseFiles in invokeLater
      assertEquals(PlainTextFileType.INSTANCE, xml.getFileType());
      var textPsi = psiManager.findFile(xml);
      assertNotNull(textPsi);
      assertNotSame(binaryPsi, textPsi);
      assertFalse(textPsi instanceof PsiBinaryFile);
    }
    finally {
      reset(manager);
    }
  }

  public void testLoadStateDoesNotLoadTheDirectory() throws IOException {
    var root = Files.createTempDirectory(Path.of(FileUtil.getTempDirectory()), "override");
    var sub = root.resolve("sub");
    var xmlPath = sub.resolve("test.xml");
    Files.createDirectories(sub);
    Files.writeString(xmlPath, "<root/>");
    var xmlSystemPath = FileUtil.toSystemIndependentName(xmlPath.toString());
    var localFileSystem = (LocalFileSystem)StandardFileSystems.local();
    var manager = emptyManager();
    try {
      manager.loadState(state(VfsUtilCore.pathToUrl(xmlSystemPath), ArchiveFileType.INSTANCE.getName()));
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); // reparseFiles in invokeLater
      assertNull(localFileSystem.findFileByPathIfCached(FileUtil.toSystemIndependentName(sub.toString())));

      var xml = localFileSystem.refreshAndFindFileByPath(xmlSystemPath);
      assertNotNull(xml);
      assertEquals(ArchiveFileType.INSTANCE, xml.getFileType());
      assertEquals(ArchiveFileType.INSTANCE.getName(), manager.getFileValue(xml));
    }
    finally {
      reset(manager);
      NioFiles.deleteRecursively(root);
      localFileSystem.refreshNioFiles(List.of(root));
    }
  }

  public void testStateWritesJarEntryUrl() throws IOException {
    var root = Files.createTempDirectory(Path.of(FileUtil.getTempDirectory()), "override");
    var jarPath = root.resolve("test.jar");
    try (var out = new ZipOutputStream(Files.newOutputStream(jarPath))) {
      out.putNextEntry(new ZipEntry("a.xml"));
      out.write("<root/>".getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
    var manager = emptyManager();
    try {
      var jar = StandardFileSystems.local().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(jarPath.toString()));
      assertNotNull(jar);
      var jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(jar);
      assertNotNull(jarRoot);
      var entry = jarRoot.findChild("a.xml");
      assertNotNull(entry);

      manager.addFile(entry, PlainTextFileType.INSTANCE);
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); // reparseFiles in invokeLater
      var file = assertOneElement(manager.getState().getChildren("file"));
      assertEquals(entry.getUrl(), file.getAttributeValue("url"));
      assertTrue(entry.getUrl(), entry.getUrl().startsWith("jar://"));
    }
    finally {
      reset(manager);
      JarFileSystemImpl.cleanupForNextTest();
      NioFiles.deleteRecursively(root);
    }
  }

  private static @NotNull Element state(@NotNull String url, @Nullable String value) {
    var file = new Element("file").setAttribute("url", url);
    if (value != null) {
      file.setAttribute("value", value);
    }
    return new Element("root").addContent(file);
  }

  private static @NotNull OverrideFileTypeManager emptyManager() {
    var manager = OverrideFileTypeManager.getInstance();
    reset(manager);
    return manager;
  }

  private static void reset(@NotNull OverrideFileTypeManager manager) {
    manager.loadState(new Element("root"));
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); // reparseFiles in invokeLater
  }

  public void testMustNotBeAbleToOverrideNotOverridableFileType() throws IOException {
    OverrideFileTypeManager manager = OverrideFileTypeManager.getInstance();
    VirtualFile dir = myFixture.getTempDirFixture().findOrCreateDir("dir");
    assertThrows(IllegalArgumentException.class, ()->manager.addFile(dir, PlainTextFileType.INSTANCE));
    VirtualFile iml = myFixture.getTempDirFixture().createFile("x.iml", "");
    assertTrue(iml.getFileType().toString(), iml.getFileType() instanceof InternalFileType);
    assertThrows(IllegalArgumentException.class, ()->manager.addFile(iml, PlainTextFileType.INSTANCE));
    VirtualFile text = myFixture.getTempDirFixture().createFile("x.txt", "");
    assertTrue(text.getFileType().toString(), text.getFileType() instanceof PlainTextFileType);
    assertThrows(IllegalArgumentException.class, ()->manager.addFile(text, ProjectFileType.INSTANCE));

    File file = new File(FileUtil.getTempDirectory() + "/x.skjdhfjksdfkjsdhf");
    FileUtil.writeToFile(file, new byte[]{1,0,2,3,4});
    VirtualFile unknown = StandardFileSystems.local().refreshAndFindFileByPath(file.getAbsolutePath());
    assertEquals(UnknownFileType.INSTANCE, unknown.getFileType());
    assertThrows(IllegalArgumentException.class, ()->manager.addFile(unknown, PlainTextFileType.INSTANCE));
    assertThrows(IllegalArgumentException.class, ()->manager.addFile(text, UnknownFileType.INSTANCE));

    FileType fakeType = new FakeFileType() {
      @Override public boolean isMyFileType(@NotNull VirtualFile file) { return false; }
      @Override public @NotNull String getName() { return "name"; }
      @Override public @Nls @NotNull String getDisplayName() { return getName(); }
      @Override public @NotNull @NlsContexts.Label String getDescription() { return getName(); }
    };
    assertThrows(IllegalArgumentException.class, ()->manager.addFile(text, fakeType));
    
    // not VirtualFileWithId
    assertThrows(IllegalArgumentException.class, ()->manager.addFile(new LightVirtualFile(), PlainTextFileType.INSTANCE));
  }

  public void testAvailableForOverride() {
    OverrideFileTypeManager manager = OverrideFileTypeManager.getInstance();

    var overrideAllowedFileType = new FakeOverridableFileType() {
      @Override
      public boolean isMyFileType(@NotNull VirtualFile file) { return false; }

      @Override
      public @NotNull String getName() { return "Foo"; }

      @Override
      public @NotNull String getDescription() { return "Foo"; }

      @Override
      public boolean isAvailableForOverride() { return true; }
    };

    VirtualFile fooFile = myFixture.getTempDirFixture().createFile("test.txt");

    manager.addFile(fooFile, overrideAllowedFileType);
    assertEquals(overrideAllowedFileType.getName(), manager.getFileValue(fooFile));

    manager.addFile(fooFile, PlainTextFileType.INSTANCE);
    assertEquals(PlainTextFileType.INSTANCE.getName(), manager.getFileValue(fooFile));

    var overrideDisallowedFileType = new FakeOverridableFileType() {
      @Override
      public boolean isMyFileType(@NotNull VirtualFile file) { return false; }

      @Override
      public @NotNull String getName() { return "FooNoOverride"; }

      @Override
      public @NotNull String getDescription() { return "FooNoOverride"; }

      @Override
      public boolean isAvailableForOverride() { return false; }
    };

    assertThrows(IllegalArgumentException.class, () -> manager.addFile(fooFile, overrideDisallowedFileType));
  }
}

abstract class FakeOverridableFileType implements FileTypeIdentifiableByVirtualFile {
  protected FakeOverridableFileType() {
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "fakeExtension";
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public boolean isBinary() {
    return true;
  }
}
