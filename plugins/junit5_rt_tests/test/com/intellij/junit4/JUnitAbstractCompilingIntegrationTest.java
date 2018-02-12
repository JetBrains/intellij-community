/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.junit4;

import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.testFramework.CompilerTester;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import java.util.List;
import java.util.stream.Collectors;

public abstract class JUnitAbstractCompilingIntegrationTest extends JUnitAbstractIntegrationTest {
  private CompilerTester myCompilerTester;
  
  protected abstract String getTestContentRoot();
  protected abstract JpsMavenRepositoryLibraryDescriptor[] getRequiredLibs();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ModuleRootModificationUtil.updateModel(myModule, 
                                           model -> model.addContentEntry(getTestContentRoot())
                                             .addSourceFolder(getTestContentRoot() + "/test", true));

    final ArtifactRepositoryManager repoManager = getRepoManager();
    for (JpsMavenRepositoryLibraryDescriptor descriptor : getRequiredLibs()) {
      addLibs(myModule, descriptor, repoManager );
    }
    myCompilerTester = new CompilerTester(myModule);
    List<CompilerMessage> compilerMessages = myCompilerTester.rebuild();
    assertEmpty(compilerMessages.stream()
                  .filter(message -> message.getCategory() == CompilerMessageCategory.ERROR)
                  .collect(Collectors.toSet()));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myCompilerTester.tearDown();
    }
    finally {
      super.tearDown();
    }
  }
}
