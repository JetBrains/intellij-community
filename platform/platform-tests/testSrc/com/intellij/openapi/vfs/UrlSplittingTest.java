/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UrlSplittingTest {
  @Test
  public void testExtractProtocol() {
    assertEquals(null, VirtualFileManager.extractProtocol(""));
    assertEquals(null, VirtualFileManager.extractProtocol("file:/"));

    assertEquals("file", VirtualFileManager.extractProtocol("file://"));
    assertEquals("file", VirtualFileManager.extractProtocol("file:///some/path/file.rb:24"));
    assertEquals("file", VirtualFileManager.extractProtocol("file://./some/path/file.rb:24"));

    assertEquals("ruby_qn", VirtualFileManager.extractProtocol("ruby_qn://"));
    assertEquals("ruby_qn", VirtualFileManager.extractProtocol("ruby_qn://A::B.method"));
  }

  @Test
  public void testExtractPath() {
    assertEquals("", VirtualFileManager.extractPath(""));
    assertEquals("file:/", VirtualFileManager.extractPath("file:/"));

    assertEquals("", VirtualFileManager.extractPath("file://"));
    assertEquals("/some/path/file.rb:24", VirtualFileManager.extractPath("file:///some/path/file.rb:24"));
    assertEquals("./some/path/file.rb:24", VirtualFileManager.extractPath("file://./some/path/file.rb:24"));

    assertEquals("", VirtualFileManager.extractPath("ruby_qn://"));
    assertEquals("A::B.method", VirtualFileManager.extractPath("ruby_qn://A::B.method"));
  }
}
