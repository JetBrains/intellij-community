// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.testFramework.JUnit38AssumeSupportRunner
import com.intellij.util.ThrowableRunnable
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.RepositoryTestLibrary
import org.junit.AssumptionViolatedException
import org.junit.runner.RunWith

@CompileStatic
@RunWith(JUnit38AssumeSupportRunner.class)
abstract class GrEclipseTestBase extends GroovyCompilerTest {

  protected abstract String getGrEclipseArtifactID()

  @Override
  protected void setUp() {
    super.setUp()
    ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(project)).defaultCompiler = new GreclipseIdeaCompiler(project)
    def jarRoot = RepositoryTestLibrary.loadRoots(project, grEclipseArtifactID)[0].file
    GreclipseIdeaCompilerSettings.getSettings(project).greclipsePath = JarFileSystem.instance.getVirtualFileForJar(jarRoot).path
  }

  @Override
  void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) {
    if (JavaSdkVersionUtil.getJavaSdkVersion(ModuleRootManager.getInstance(module).sdk)?.isAtLeast(JavaSdkVersion.JDK_10)) {
      throw new AssumptionViolatedException("Groovy-Eclipse doesn't support Java 10+ yet")
    }
    super.runTestRunnable(testRunnable)
  }

  protected List<String> chunkRebuildMessage(String builder) {
    return []
  }
}
