/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class ConvertExcludedToIgnoredTest extends PlatformTestCase {
  private VirtualFile myContentRoot;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myContentRoot = getVirtualFile(createTempDirectory());
    PsiTestUtil.addContentRoot(myModule, myContentRoot);
  }

  public void testExcludedFolder() {
    VirtualFile excluded = createChildDirectory(myContentRoot, "exc");
    PsiTestUtil.addExcludedRoot(myModule, excluded);
    getChangeListManager().convertExcludedToIgnored();
    assertFalse(getChangeListManager().isIgnoredFile(myContentRoot));
    assertTrue(getChangeListManager().isIgnoredFile(excluded));
    assertIgnored(excluded);
  }

  public void testModuleOutput() {
    VirtualFile output = createChildDirectory(myContentRoot, "out");
    PsiTestUtil.setCompilerOutputPath(myModule, output.getUrl(), false);
    getChangeListManager().convertExcludedToIgnored();
    assertFalse(getChangeListManager().isIgnoredFile(myContentRoot));
    assertTrue(getChangeListManager().isIgnoredFile(output));
    assertIgnored(output);
  }

  public void testProjectOutput() throws IOException {
    VirtualFile output = getVirtualFile(createTempDir("projectOutput"));
    CompilerProjectExtension.getInstance(getProject()).setCompilerOutputUrl(output.getUrl());
    getChangeListManager().convertExcludedToIgnored();
    assertTrue(getChangeListManager().isIgnoredFile(output));
    assertIgnored(output);
  }

  public void testModuleOutputUnderProjectOutput() throws IOException {
    VirtualFile output = getVirtualFile(createTempDir("projectOutput"));
    CompilerProjectExtension.getInstance(getProject()).setCompilerOutputUrl(output.getUrl());
    VirtualFile moduleOutput = createChildDirectory(output, "module");
    PsiTestUtil.setCompilerOutputPath(myModule, moduleOutput.getUrl(), false);
    getChangeListManager().convertExcludedToIgnored();
    assertTrue(getChangeListManager().isIgnoredFile(output));
    assertTrue(getChangeListManager().isIgnoredFile(moduleOutput));
    assertIgnored(output);
  }

  public void testModuleOutputUnderExcluded() {
    VirtualFile excluded = createChildDirectory(myContentRoot, "target");
    PsiTestUtil.addExcludedRoot(myModule, excluded);
    VirtualFile moduleOutput = createChildDirectory(excluded, "classes");
    PsiTestUtil.setCompilerOutputPath(myModule, moduleOutput.getUrl(), false);
    getChangeListManager().convertExcludedToIgnored();
    assertTrue(getChangeListManager().isIgnoredFile(excluded));
    assertTrue(getChangeListManager().isIgnoredFile(moduleOutput));
    assertIgnored(excluded);
  }

  public void testDoNotIgnoreInnerModuleExplicitlyMarkedAsExcludedFromOuterModule() {
    VirtualFile inner = createChildDirectory(myContentRoot, "inner");
    PsiTestUtil.addModule(myProject, ModuleType.EMPTY, "inner", inner);
    PsiTestUtil.addExcludedRoot(myModule, inner);
    getChangeListManager().convertExcludedToIgnored();
    assertFalse(getChangeListManager().isIgnoredFile(inner));
  }

  private void assertIgnored(@NotNull VirtualFile... ignoredDirs) {
    assertIgnoredDirectories(getProject(), ignoredDirs);
  }

  public static void assertIgnoredDirectories(@NotNull Project project, @NotNull VirtualFile... expectedIgnoredDirs) {
    List<String> expectedIgnoredPaths = new ArrayList<>();
    for (VirtualFile dir : expectedIgnoredDirs) {
      expectedIgnoredPaths.add(dir.getPath()+"/");
    }
    List<String> actualIgnoredPaths = ContainerUtil.map2List(ChangeListManagerImpl.getInstanceImpl(project).getFilesToIgnore(), bean -> bean.getPath());
    assertSameElements(actualIgnoredPaths, expectedIgnoredPaths);
  }

  private ChangeListManagerImpl getChangeListManager() {
    return ChangeListManagerImpl.getInstanceImpl(getProject());
  }
}
