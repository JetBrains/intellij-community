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
package com.intellij.psi.search;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;

public class GlobalSearchScopeTest extends PlatformTestCase {
  public void testUniteDirectorySearchScopeDoesNotSOE() throws Exception {
    VirtualFile genRoot = getVirtualFile(createTempDir("genSrcRoot"));
    VirtualFile srcRoot = getVirtualFile(createTempDir("srcRoot"));
    VirtualFile child = srcRoot.createChildDirectory(this, "child");
    GlobalSearchScope childScope = GlobalSearchScopesCore.directoryScope(getProject(), child, true);

    GlobalSearchScope directoryScope = GlobalSearchScopesCore.directoryScope(getProject(), srcRoot, true);
    GlobalSearchScope scope = GlobalSearchScope.EMPTY_SCOPE.uniteWith(directoryScope);
    assertSame(scope, directoryScope);
    scope = scope.uniteWith(directoryScope);
    assertSame(scope, directoryScope);

    scope = scope.uniteWith(childScope);
    assertSame(scope, directoryScope);

    GlobalSearchScope s = childScope;
    int N = 1000;
    VirtualFile[] d = new VirtualFile[N];
    for (int i=0; i< N;i++) {
      d[i] = srcRoot.createChildDirectory(this, "d"+i);
      GlobalSearchScope united = s.uniteWith(GlobalSearchScopesCore.directoryScope(getProject(), d[i], true));
      assertNotSame(s, united);
      s = united;
      assertTrue(s instanceof GlobalSearchScopesCore.DirectoriesScope);
    }
    for (VirtualFile file : d) {
      VirtualFile f = createChildData(file, "f");
      assertTrue(s.contains(f));
    }
    assertFalse(s.contains(genRoot));

    assertSame(s.uniteWith(childScope), s);
    assertSame(s.uniteWith(s), s);
  }
}