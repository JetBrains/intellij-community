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
package com.intellij.openapi.util.io;

import com.intellij.util.Processor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 11/22/12
 * Time: 11:41 AM
 */
public class FileUtilHeavyTest {
  private File myTempDirectory;

  @Before
  public void setUp() throws Exception {
    myTempDirectory = FileUtil.createTempDirectory(getClass().getSimpleName() + ".", ".tmp");
  }

  @After
  public void tearDown() throws Exception {
    if (myTempDirectory != null) {
      FileUtil.delete(myTempDirectory);
    }
  }

  @Test
  public void testSimpleRecursiveIteration() throws Exception {
    final Tree tree = new Tree(myTempDirectory);
    final Map<String, Integer> result = new HashMap<String, Integer>();
    FileUtil.processFilesRecursively(myTempDirectory, new Processor<File>() {
      @Override
      public boolean process(File file) {
        final Integer integer = result.get(file.getName());
        result.put(file.getName(), integer == null ? 1 : (integer + 1));
        return true;
      }
    });

    Assert.assertEquals(6, result.size());
    Assert.assertEquals(1, result.get(myTempDirectory.getName()).intValue());
    Assert.assertEquals(3, result.get("1").intValue());
    Assert.assertEquals(3, result.get("2").intValue());
    Assert.assertEquals(1, result.get("dir1").intValue());
  }

  @Test
  public void testStops() throws Exception {
    final Tree tree = new Tree(myTempDirectory);
    final int[] cnt = new int[]{0};
    FileUtil.processFilesRecursively(myTempDirectory, new Processor<File>() {
      @Override
      public boolean process(File file) {
        ++ cnt[0];
        return false;
      }
    });
    Assert.assertEquals(1, cnt[0]);
  }

  @Test
  public void testDirectoryFilter() throws Exception {
    final Tree tree = new Tree(myTempDirectory);
    final Map<String, Integer> result = new HashMap<String, Integer>();
    FileUtil.processFilesRecursively(myTempDirectory, new Processor<File>() {
      @Override
      public boolean process(File file) {
        final Integer integer = result.get(file.getName());
        result.put(file.getName(), integer == null ? 1 : (integer + 1));
        return true;
      }
    }, new Processor<File>() {
                                       @Override
                                       public boolean process(File file) {
                                         return ! "dir2".equals(file.getName());
                                       }
                                     });
    Assert.assertEquals(5, result.size());
    Assert.assertEquals(1, result.get(myTempDirectory.getName()).intValue());
    Assert.assertEquals(1, result.get("1").intValue());
    Assert.assertEquals(1, result.get("2").intValue());
    Assert.assertEquals(1, result.get("dir1").intValue());
    Assert.assertEquals(1, result.get("dir2").intValue());
    Assert.assertNull(result.get("dir21"));
  }

  private static class Tree {
    private final File dir1;
    private final File file11;
    private final File file12;

    private final File dir2;
    private final File file21;
    private final File file22;

    private final File dir21;
    private final File file211;
    private final File file212;

    private Tree(final File root) throws IOException {
      dir1 = new File(root, "dir1");
      dir2 = new File(root, "dir2");
      dir21 = new File(dir2, "inner");

      Assert.assertTrue(dir1.mkdir());
      Assert.assertTrue(dir2.mkdir());
      Assert.assertTrue(dir21.mkdir());

      file11 = new File(dir1, "1");
      file12 = new File(dir1, "2");

      file21 = new File(dir2, "1");
      file22 = new File(dir2, "2");

      file211 = new File(dir21, "1");
      file212 = new File(dir21, "2");

      file11.createNewFile();
      file12.createNewFile();

      file21.createNewFile();
      file22.createNewFile();

      file211.createNewFile();
      file212.createNewFile();
    }
  }
}
