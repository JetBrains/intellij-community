// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FileTypeIndexImpl;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class FileTypeIndexTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testAddFileType() {
    addAndRemoveFileType();
  }

  static void addAndRemoveFileType() {
    FileType foo = new FakeFileType() {
      @Override
      public boolean isMyFileType(@NotNull VirtualFile file) {
        return true;
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
    FileTypeIndexImpl index = new FileTypeIndexImpl();
    int version = index.getVersion();

    try {
      FileTypeManagerEx.getInstanceEx().registerFileType(foo);
      assertNotSame(version, index.getVersion());
    }
    finally {
      FileTypeManagerEx.getInstanceEx().unregisterFileType(foo);
    }
  }
}
