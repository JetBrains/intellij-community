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
package org.jetbrains.plugins.groovy.compiler

import com.intellij.compiler.options.ValidationConfiguration
import com.intellij.openapi.compiler.CompilerMessage
import com.intellij.openapi.compiler.options.ExcludeEntryDescription
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.PsiTestUtil
import groovy.transform.CompileStatic
import org.jetbrains.jps.model.java.JavaResourceRootType
/**
 * @author peter
 */
@CompileStatic
class GroovyResourceCheckerTest extends GroovyCompilerTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp()
    PsiTestUtil.removeAllRoots(myModule, ModuleRootManager.getInstance(myModule).sdk)
    addGroovyLibrary(myModule)
    PsiTestUtil.addSourceRoot(myModule, myFixture.tempDirFixture.findOrCreateDir('src'))
    PsiTestUtil.addSourceRoot(myModule, myFixture.tempDirFixture.findOrCreateDir('res'), JavaResourceRootType.RESOURCE)
  }

  private List<CompilerMessage> checkResources() {
    return myCompilerTester.runCompiler { CheckResourcesAction.checkResources(project, it, false) }
  }

  void "test basic errors"() {
    def file = myFixture.addFileToProject('res/a.groovy', 'class Foo extends Bar {}')
    shouldFail { checkResources() }

    setFileText file, 'class F oo {}'
    shouldFail { checkResources() }

    setFileText file, 'class Foo {}'
    assertEmpty checkResources()
  }

  void "test depend on source and other resources"() {
    Module depModule = addModule("dependent", false)
    ModuleRootModificationUtil.addDependency(myModule, depModule)
    addGroovyLibrary(depModule)
    PsiTestUtil.addSourceRoot(depModule, myFixture.tempDirFixture.findOrCreateDir('dependent/src'))
    PsiTestUtil.addSourceRoot(depModule, myFixture.tempDirFixture.findOrCreateDir('dependent/res'), JavaResourceRootType.RESOURCE)

    myFixture.addFileToProject('src/a.groovy', 'class SrcClass {}')
    myFixture.addFileToProject('res/b.groovy', 'interface ThisResource {}')
    myFixture.addFileToProject('dependent/src/a.groovy', 'interface DependentSrc {}')
    myFixture.addFileToProject('dependent/res/b.groovy', 'interface DependentResource {}')


    myFixture.addFileToProject('res/a.groovy', 'class Foo extends SrcClass implements ThisResource, DependentSrc, DependentResource {}')
    assertEmpty checkResources()
  }

  void "test exclude from validation"() {
    checkResources()
    def file = myFixture.addFileToProject('res/a.groovy', 'class Foo extends Bar {}')
    ValidationConfiguration.getExcludedEntriesConfiguration(project).addExcludeEntryDescription(new ExcludeEntryDescription(file.virtualFile, false, true, project))
    assertEmpty checkResources()
  }

}
