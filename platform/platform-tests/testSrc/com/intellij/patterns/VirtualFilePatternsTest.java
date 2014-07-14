/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.patterns;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

/**
 * @author peter
 */
public class VirtualFilePatternsTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testWithSuperParent() {
    VirtualFile file = myFixture.addFileToProject("foo/bar.txt", "").getVirtualFile();
    assert PlatformPatterns.virtualFile().withSuperParent(1, PlatformPatterns.virtualFile().withName("foo")).accepts(file);
    assert !PlatformPatterns.virtualFile().withSuperParent(1, PlatformPatterns.virtualFile().withName("bar")).accepts(file);
    assert !PlatformPatterns.virtualFile().withSuperParent(2, PlatformPatterns.virtualFile().withName("bar")).accepts(file);
    assert !PlatformPatterns.virtualFile().withSuperParent(10, PlatformPatterns.virtualFile().withName("bar")).accepts(file);
    assert !PlatformPatterns.virtualFile().withSuperParent(10, PlatformPatterns.virtualFile().withName("foo")).accepts(file);
  }
}
