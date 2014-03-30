/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.execution.util.ExecUtil;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;

public class ResourceIntegrityTest {
  private final ClassLoader myClassLoader = CreateLauncherScriptAction.class.getClassLoader();

  @Test
  public void launcher() throws Exception {
    String contents = ExecUtil.loadTemplate(myClassLoader, "launcher.py", Collections.<String, String>emptyMap());
    assertFalse(contents.contains("\r\n"));
  }

  @Test
  public void desktopEntry() throws Exception {
    String contents = ExecUtil.loadTemplate(myClassLoader, "entry.desktop", Collections.<String, String>emptyMap());
    assertFalse(contents.contains("\r\n"));
  }
}
