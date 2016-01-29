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
package com.intellij.execution.process;

import com.intellij.testFramework.UsefulTestCase;

public class ProcessInfoTest extends UsefulTestCase {
  public void testExecutableName() throws Exception {
    assertEquals("foo", new ProcessInfo(1, "", "foo", "").getExecutableDisplayName());
    assertEquals("foo", new ProcessInfo(1, "", "foo.exe", "").getExecutableDisplayName());
    assertEquals("foo", new ProcessInfo(1, "", "foo.EXE", "").getExecutableDisplayName());
    assertEquals("foo.bar", new ProcessInfo(1, "", "foo.bar", "").getExecutableDisplayName());
    assertEquals("foo.1.2", new ProcessInfo(1, "", "foo.1.2", "").getExecutableDisplayName());
  }
}