// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler

import com.intellij.compiler.options.ValidationConfiguration
import com.intellij.openapi.compiler.CompilerMessage
import com.intellij.openapi.compiler.options.ExcludeEntryDescription
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.PsiTestUtil
import groovy.transform.CompileStatic
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
@CompileStatic
class GroovyResourceCheckerTest extends GroovyCompilerTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp()
    PsiTestUtil.removeAllRoots(module, ModuleRootManager.getInstance(module).sdk)
    addGroovyLibrary(module)
    PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir('src'))
    PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir('res'), (JpsModuleSourceRootType<JavaResourceRootProperties>)JavaResourceRootType.RESOURCE)
  }

  private List<CompilerMessage> checkResources() {
    return myCompilerTester.runCompiler { CheckResourcesAction.checkResources(project, it, false) }
  }

  void "test basic errors"() {
    def file = myFixture.addFileToProject('res/a.groovy', 'class Foo extends Bar {}')
    shouldFail checkResources() 

    setFileText file, 'class F oo {}'
    shouldFail checkResources()

    setFileText file, 'class Foo {}'
    assertEmpty checkResources()
  }

  void "test depend on source and other resources"() {
    Module depModule = addModule("dependent", false)
    ModuleRootModificationUtil.addDependency(module, depModule)
    addGroovyLibrary(depModule)
    PsiTestUtil.addSourceRoot(depModule, myFixture.tempDirFixture.findOrCreateDir('dependent/src'))
    PsiTestUtil.addSourceRoot(depModule, myFixture.tempDirFixture.findOrCreateDir('dependent/res'), (JpsModuleSourceRootType<JavaResourceRootProperties>)JavaResourceRootType.RESOURCE)

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

  void "test stop after errors in one module"() {
    Module depModule = addModule("dependent", false)
    ModuleRootModificationUtil.addDependency(depModule, module)
    addGroovyLibrary(depModule)
    PsiTestUtil.addSourceRoot(depModule, myFixture.tempDirFixture.findOrCreateDir('dependent/res'), (JpsModuleSourceRootType<JavaResourceRootProperties>)JavaResourceRootType.RESOURCE)

    myFixture.addFileToProject('res/Util.groovy', '@groovy.transform.CompileStatic class C1 {{ println Xxx1.name }}')
    myFixture.addFileToProject('dependent/res/Usage.groovy', '@groovy.transform.CompileStatic class C2 {{ println Xxx2.name }}')

    def messages = checkResources()
    assert messages.find { it.message.contains('Xxx1') }
    assert !messages.find { it.message.contains('Xxx2') }
  }

}
