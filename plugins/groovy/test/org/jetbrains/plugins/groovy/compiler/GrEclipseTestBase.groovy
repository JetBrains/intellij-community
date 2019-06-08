// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.compiler

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil
import com.intellij.openapi.roots.ModuleRootManager
import groovy.transform.CompileStatic

@CompileStatic
abstract class GrEclipseTestBase extends GroovyCompilerTest {

  protected abstract String getGrEclipsePath()

  @Override
  protected void setUp() {
    super.setUp()
    ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(project)).defaultCompiler = new GreclipseIdeaCompiler(project)
    GreclipseIdeaCompilerSettings.getSettings(project).greclipsePath = grEclipsePath
  }

  @Override
  void runTest() {
    if (JavaSdkVersionUtil.getJavaSdkVersion(ModuleRootManager.getInstance(module).sdk)?.isAtLeast(JavaSdkVersion.JDK_10)) {
      println "Groovy-Eclipse doesn't support Java 10+ yet"
      return
    }
    super.runTest()
  }

  protected List<String> chunkRebuildMessage(String builder) {
    return []
  }
}
