/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightPlatformLangTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;

import java.io.File;
import java.io.IOException;

public class GetPathPerformanceTest extends LightPlatformLangTestCase {
  public void testGetPath() throws IOException, InterruptedException {
    final File dir = FileUtil.createTempDirectory("GetPath","");
    disposeOnTearDown(new Disposable() {
      @Override
      public void dispose() {
        FileUtil.delete(dir);
      }
    });

    String path = dir.getPath() + StringUtil.repeat("/xxx", 50) + "/fff.txt";
    File ioFile = new File(path);
    boolean b = ioFile.getParentFile().mkdirs();
    assertTrue(b);
    boolean c = ioFile.createNewFile();
    assertTrue(c);
    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(ioFile.getPath().replace(File.separatorChar, '/'));
    assertNotNull(file);

    PlatformTestUtil.startPerformanceTest("VF.getPath() performance failed", 4000, new ThrowableRunnable() {
      @Override
      public void run() {
        for (int i = 0; i < 1000000; ++i) {
          file.getPath();
        }
      }
    }).cpuBound().assertTiming();
  }
}
