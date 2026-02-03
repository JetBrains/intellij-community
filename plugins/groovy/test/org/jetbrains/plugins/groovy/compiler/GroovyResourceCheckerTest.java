// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.options.ValidationConfiguration;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.io.IOException;
import java.util.List;

public class GroovyResourceCheckerTest extends GroovyCompilerTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PsiTestUtil.removeAllRoots(getModule(), ModuleRootManager.getInstance(getModule()).getSdk());
    addGroovyLibrary(getModule());
    PsiTestUtil.addSourceRoot(getModule(), myFixture.getTempDirFixture().findOrCreateDir("src"));
    PsiTestUtil.addSourceRoot(getModule(), myFixture.getTempDirFixture().findOrCreateDir("res"), JavaResourceRootType.RESOURCE);
  }

  private List<CompilerMessage> checkResources() {
    return myCompilerTester.runCompiler(n -> CheckResourcesAction.checkResources(getProject(), n, false));
  }

  public void test_basic_errors() throws IOException {
    PsiFile file = myFixture.addFileToProject("res/a.groovy", "class Foo extends Bar {}");
    shouldFail(checkResources());

    setFileText(file, "class F oo {}");
    shouldFail(checkResources());

    setFileText(file, "class Foo {}");
    assertEmpty(checkResources());
  }

  public void test_depend_on_source_and_other_resources() throws IOException {
    Module depModule = addModule("dependent", false);
    ModuleRootModificationUtil.addDependency(getModule(), depModule);
    addGroovyLibrary(depModule);
    PsiTestUtil.addSourceRoot(depModule, myFixture.getTempDirFixture().findOrCreateDir("dependent/src"));
    PsiTestUtil.addSourceRoot(depModule, myFixture.getTempDirFixture().findOrCreateDir("dependent/res"), JavaResourceRootType.RESOURCE);

    myFixture.addFileToProject("src/a.groovy", "class SrcClass {}");
    myFixture.addFileToProject("res/b.groovy", "interface ThisResource {}");
    myFixture.addFileToProject("dependent/src/a.groovy", "interface DependentSrc {}");
    myFixture.addFileToProject("dependent/res/b.groovy", "interface DependentResource {}");
    myFixture.addFileToProject("res/a.groovy", "class Foo extends SrcClass implements ThisResource, DependentSrc, DependentResource {}");
    assertEmpty(checkResources());
  }

  public void test_exclude_from_validation() {
    checkResources();
    PsiFile file = myFixture.addFileToProject("res/a.groovy", "class Foo extends Bar {}");
    ValidationConfiguration.getExcludedEntriesConfiguration(getProject())
      .addExcludeEntryDescription(new ExcludeEntryDescription(file.getVirtualFile(), false, true, getProject()));
    assertEmpty(checkResources());
  }

  public void test_stop_after_errors_in_one_module() throws IOException {
    Module depModule = addModule("dependent", false);
    ModuleRootModificationUtil.addDependency(depModule, getModule());
    addGroovyLibrary(depModule);
    PsiTestUtil.addSourceRoot(depModule, myFixture.getTempDirFixture().findOrCreateDir("dependent/res"), JavaResourceRootType.RESOURCE);

    myFixture.addFileToProject("res/Util.groovy", "@groovy.transform.CompileStatic class C1 {{ println Xxx1.name }}");
    myFixture.addFileToProject("dependent/res/Usage.groovy", "@groovy.transform.CompileStatic class C2 {{ println Xxx2.name }}");

    List<CompilerMessage> messages = checkResources();
    assertTrue(ContainerUtil.exists(messages, m -> m.getMessage().contains("Xxx1")));
    assertFalse(ContainerUtil.exists(messages, m -> m.getMessage().contains("Xxx2")));
  }
}
