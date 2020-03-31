// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.compiler

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.JarFileSystem
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.RepositoryTestLibrary

@CompileStatic
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
