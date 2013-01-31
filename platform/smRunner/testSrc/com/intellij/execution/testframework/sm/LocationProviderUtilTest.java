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
package com.intellij.execution.testframework.sm;

import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.UsefulTestCase;

/**
 * @author Roman Chernyatchik
 */
public class LocationProviderUtilTest extends UsefulTestCase {
  public void testExtractProtocol() {
    assertEquals(null,
                 VirtualFileManager.extractProtocol(""));
    assertEquals(null,
                 VirtualFileManager.extractProtocol("file:/"));

    assertEquals("file",
                 VirtualFileManager.extractProtocol("file://"));
    assertEquals("file",
                 VirtualFileManager.extractProtocol("file:///some/path/file.rb:24"));
    assertEquals("file",
                 VirtualFileManager.extractProtocol("file://./some/path/file.rb:24"));

    assertEquals("ruby_qn",
                 VirtualFileManager.extractProtocol("ruby_qn://"));
    assertEquals("ruby_qn",
                 VirtualFileManager.extractProtocol("ruby_qn://A::B.method"));
  }

  public void testExtractPath() {
    assertEquals(null,
                 TestsLocationProviderUtil.extractPath(""));
    assertEquals(null,
                 TestsLocationProviderUtil.extractPath("file:/"));

    assertEquals("",
                 TestsLocationProviderUtil.extractPath("file://"));
    assertEquals("/some/path/file.rb:24",
                 TestsLocationProviderUtil.extractPath("file:///some/path/file.rb:24"));
    assertEquals("./some/path/file.rb:24",
                 TestsLocationProviderUtil.extractPath("file://./some/path/file.rb:24"));

    assertEquals("",
                 TestsLocationProviderUtil.extractPath("ruby_qn://"));
    assertEquals("A::B.method", 
                 TestsLocationProviderUtil.extractPath("ruby_qn://A::B.method"));
  }
}
