// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.FileTypeIndexImpl;
import com.intellij.psi.search.FileTypeIndexImplBase;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Dmitry Avdeev
 */
public class FileTypeIndexTest extends BasePlatformTestCase {
  public void testAddFileType() {
    FileTypeIndexImpl index = FileBasedIndexExtension.EXTENSION_POINT_NAME.findExtension(FileTypeIndexImpl.class);
    VirtualFile file = myFixture.configureByText("foo.test", "abc").getVirtualFile();
    FileTypeIndex.getFiles(PlainTextFileType.INSTANCE, GlobalSearchScope.allScope(getProject()));

    int version = index.getVersion();
    Disposable disposable = Disposer.newDisposable();
    FileType foo = registerFakeFileType(getTestName(false), "test", disposable);
    //PsiTestUtil.waitUntilIndexesAreReady(getProject());
    try {
      assertEquals(version, index.getVersion());
      Collection<VirtualFile> files = FileTypeIndex.getFiles(foo, GlobalSearchScope.allScope(getProject()));
      assertOneElement(files);
      assertEquals(foo, FileTypeIndex.getIndexedFileType(file, getProject()));
    }
    finally {
      Disposer.dispose(disposable);
      IndexingTestUtil.waitUntilIndexesAreReady(getProject());
    }
    assertEquals(PlainTextFileType.INSTANCE, FileTypeIndex.getIndexedFileType(file, getProject()));
    assertEmpty(FileTypeIndex.getFiles(foo, GlobalSearchScope.allScope(getProject())));
  }

  public void testFileTypeChangeGetsIndexed() throws IOException {
    var disposable = Disposer.newDisposable();
    var sourceRoot = LightPlatformTestCase.getSourceRoot();
    var file = WriteAction.compute(() -> {
      var f = sourceRoot.findOrCreateChildData(this, "filename.smth1");
      VfsUtil.saveText(f, "text");
      return f;
    });
    assertEquals(PlainTextFileType.INSTANCE,
                 ReadAction.compute(() -> {
                   return FileTypeIndex.getIndexedFileType(file, getProject());
                 }));
    var smth1 = registerFakeFileType("test filetype 1", "smth1", disposable);
    var smth2 = registerFakeFileType("test filetype 2", "smth2", disposable);
    assertEquals(smth1, FileTypeIndex.getIndexedFileType(file, getProject()));
    assertOneElement(FileTypeIndex.getFiles(smth1, GlobalSearchScope.allScope(getProject())));
    assertEmpty(FileTypeIndex.getFiles(smth2, GlobalSearchScope.allScope(getProject())));
    WriteAction.run(() -> {
      file.rename(this, "filename.smth2");
    });
    assertEquals(smth2, FileTypeIndex.getIndexedFileType(file, getProject()));
    assertEmpty(FileTypeIndex.getFiles(smth1, GlobalSearchScope.allScope(getProject())));
    assertOneElement(FileTypeIndex.getFiles(smth2, GlobalSearchScope.allScope(getProject())));
    Disposer.dispose(disposable);
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
    assertEquals(PlainTextFileType.INSTANCE, FileTypeIndex.getIndexedFileType(file, getProject()));
  }

  public void testChangeNotificationsWork() throws IOException {
    Disposable disposable = Disposer.newDisposable();
    var testFiletype = registerFakeFileType("test filetype", "smth", disposable);
    var notifications = setupNotificationsCounter();

    ensureUpToDate();
    flushNotifications();
    assertEquals(0, (int)notifications.getOrDefault(testFiletype, 0));

    var file = WriteAction.compute(() -> {
      var f = LightPlatformTestCase.getSourceRoot().findOrCreateChildData(this, "filename.smth");
      VfsUtil.saveText(f, "text");
      return f;
    });
    ensureUpToDate();
    flushNotifications();
    assertEquals(1, (int)notifications.getOrDefault(testFiletype, 0));

    WriteAction.compute(() -> {
      file.rename(this, "filename.notsmth");
      return null;
    });
    ensureUpToDate();
    flushNotifications();
    assertEquals(2, (int)notifications.getOrDefault(testFiletype, 0));
    Disposer.dispose(disposable);
  }

  public void testEventualNotificationReceipt() throws IOException, InterruptedException {
    var testFiletype = registerFakeFileType("test filetype", "smth", getTestRootDisposable());
    var notifications = setupNotificationsCounter();

    ensureUpToDate();
    assertEquals(0, (int)notifications.getOrDefault(testFiletype, 0));

    WriteAction.compute(() -> {
      var f = LightPlatformTestCase.getSourceRoot().findOrCreateChildData(this, "filename.smth");
      VfsUtil.saveText(f, "text");
      return f;
    });
    ensureUpToDate();

    for (int i = 0; i < 10; i++) {
      if (notifications.getOrDefault(testFiletype, 0) != 1) {
        Thread.sleep(1);
      }
    }
    assertEquals(1, (int)notifications.getOrDefault(testFiletype, 0));
  }

  private void ensureUpToDate() {
    FileBasedIndex.getInstance().ensureUpToDate(FileTypeIndex.NAME, getProject(), GlobalSearchScope.allScope(getProject()));
  }

  private static void flushNotifications() {
    var index = ((FileBasedIndexEx)FileBasedIndex.getInstance()).getIndex(FileTypeIndex.NAME);
    ((FileTypeIndexImplBase)index).processPendingNotifications();
  }

  private ConcurrentHashMap<FileType, Integer> setupNotificationsCounter() {
    var notifications = new ConcurrentHashMap<FileType, Integer>();
    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable()).subscribe(
      FileTypeIndex.INDEX_CHANGE_TOPIC,
      new FileTypeIndex.IndexChangeListener() {
        @Override
        public void onChangedForFileType(@NotNull FileType fileType) {
          notifications.compute(fileType, (__, val) -> {
            if (val == null) {
              return 1;
            }
            else {
              return val + 1;
            }
          });
        }
      }
    );
    return notifications;
  }

  /**
   * @param extension filetype extension without preceding dot (e.g. "test" to match *.test files)
   */
  public static @NotNull FileType registerFakeFileType(@NotNull String name, @NotNull String extension, @NotNull Disposable parent) {
    FileType foo = new FakeFileType() {
      @Override
      public boolean isMyFileType(@NotNull VirtualFile file) { return file.getName().endsWith("." + extension); }

      @Override
      public @NotNull String getName() { return name; }

      @Override
      public @NotNull String getDescription() { return name; }
    };
    ((FileTypeManagerImpl)FileTypeManager.getInstance()).registerFileType(foo, List.of(), parent,
                                                                          PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID));
    IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects();
    return foo;
  }
}
