/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.core.tree;

import com.intellij.history.core.Paths;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;

public class PathsIterationTest extends TestCase {
  public void testPaths() {
    testPathSplit("relative/folder/file.txt", "relative", "folder", "file.txt");
    testPathSplit("C:/Users/user/folder/file.txt", "C:", "Users", "user", "folder", "file.txt");
    testPathSplit("//wsl$/Distro/home/user/folder/file.txt", "//wsl$", "Distro", "home", "user", "folder", "file.txt");
    testPathSplit("//wsl$/Distro", "//wsl$", "Distro");
    testPathSplit("//wsl$/", "//wsl$");
    testPathSplit("/home/user/folder/file.txt", "/", "home", "user", "folder", "file.txt");
    testPathSplit("/", "/");
  }

  private static void testPathSplit(String path, String... expectedElements) {
    List<String> actualElements = ContainerUtil.collect(Paths.split(path).iterator());
    assertEquals(path, Arrays.asList(expectedElements), actualElements);
  }
}
