/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.testFramework.vcs;

import com.intellij.openapi.vfs.VirtualFile;
import junit.framework.Assert;
import org.junit.Test;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/19/12
 * Time: 8:24 PM
 */
public class DirectoryDataTest extends FileBasedTest {
  @Test
  public void testQuadro() throws Exception {
    final DirectoryData data = new DirectoryData(myProject.getBaseDir(), 2, 2, ".txt");
    data.clear();
    try {
      data.create();

      final String[] dirs = {"DL0N0", "DL0N1"};
      final String[] files = {"FL0N0.txt", "FL0N1.txt", "DL0N0/FL00N0.txt", "DL0N0/FL00N1.txt", "DL0N1/FL01N0.txt", "DL0N1/FL01N1.txt"};

      for (String dir : dirs) {
        final VirtualFile vDir = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), dir));
        Assert.assertTrue(vDir != null);
        Assert.assertTrue(vDir.exists());
        Assert.assertTrue(vDir.isDirectory());
      }

      for (String file : files) {
        final VirtualFile vFile = myLocalFileSystem.refreshAndFindFileByIoFile(new File(data.getBase().getPath(), file));
        Assert.assertTrue(vFile != null);
        Assert.assertTrue(vFile.exists());
        Assert.assertTrue(! vFile.isDirectory());
      }
    }
    finally {
      data.clear();
    }
  }
}
