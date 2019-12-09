// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.FileTypeIndexImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public class FileTypeIndexTest extends BasePlatformTestCase {

  public void testAddFileType() {
    FileTypeIndexImpl index = FileBasedIndexExtension.EXTENSION_POINT_NAME.findExtension(FileTypeIndexImpl.class);
    myFixture.configureByText("foo.test", "abc");
    FileTypeIndex.getFiles(PlainTextFileType.INSTANCE, GlobalSearchScope.allScope(getProject()));

    FileType foo = registerFakeFileType();
    int version = index.getVersion();
    try {
      assertNotSame(version, index.getVersion());
      Collection<VirtualFile> files = FileTypeIndex.getFiles(foo, GlobalSearchScope.allScope(getProject()));
      assertEquals(1, files.size());
    }
    finally {
      FileTypeManagerEx.getInstanceEx().unregisterFileType(foo);
    }
  }

  @NotNull
   static FileType registerFakeFileType() {
    FileType foo = new FakeFileType() {
      @Override
      public boolean isMyFileType(@NotNull VirtualFile file) {
        return file.getName().equals("foo.test");
      }

      @NotNull
      @Override
      public String getName() {
        return "foo";
      }

      @NotNull
      @Override
      public String getDescription() {
        return "";
      }
    };

    FileTypeManagerEx.getInstanceEx().registerFileType(foo);
    return foo;
  }
}
