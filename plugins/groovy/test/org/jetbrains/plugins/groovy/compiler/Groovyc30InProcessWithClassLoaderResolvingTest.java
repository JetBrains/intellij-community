// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants;
import org.jetbrains.jps.incremental.groovy.JpsGroovycRunner;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.TestLibrary;

import java.io.IOException;

public final class Groovyc30InProcessWithClassLoaderResolvingTest extends GroovycTestBase {
  @Override
  protected TestLibrary getGroovyLibrary() {
    return GroovyProjectDescriptors.LIB_GROOVY_3_0;
  }

  @Override
  public void testRecompileOneFileThatTriggersChunkRebuildInside() throws IOException {
    doTestRecompileOneFileThatTriggersChunkRebuildInside(false);
  }

  @Override
  public void testExtendGroovyClassesWithAdditionalDependencies() {
    ModuleRootModificationUtil.updateModel(getModule(), model -> {
      MavenDependencyUtil.addFromMaven(model, "org.codehaus.groovy:groovy-test:3.0.20", false);
    });
    super.testExtendGroovyClassesWithAdditionalDependencies();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(getProject());
    compilerConfiguration.setBuildProcessVMOptions(
      compilerConfiguration.getBuildProcessVMOptions() +
      " -D" + JpsGroovycRunner.GROOVYC_IN_PROCESS + "=true" +
      " -D" + GroovyRtConstants.GROOVYC_ASM_RESOLVING_ONLY + "=false"
    );
  }

  public void testDependentTrait() throws IOException {
    PsiFile ca = myFixture.addFileToProject("A.groovy", "class A implements B { }");
    myFixture.addFileToProject("B.groovy", "trait B { A aaa }");
    UsefulTestCase.assertEmpty(make());
    touch(ca.getVirtualFile());
    UsefulTestCase.assertEmpty(make());
  }
}
