// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.testFramework.JUnit38AssumeSupportRunner
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.RepositoryTestLibrary
import org.junit.runner.RunWith

@CompileStatic
@RunWith(JUnit38AssumeSupportRunner.class)
abstract class GrEclipseTestBase extends GroovyCompilerTest {
  public static final RemoteRepositoryDescription GROOVY_PLUGIN_RELEASE = new RemoteRepositoryDescription(
    "groovy.plugin.release",
    "JFrog Groovy Plugin Release repository",
    "https://groovy.jfrog.io/artifactory/plugins-release-local/"
  );

  protected abstract String getGrEclipseArtifactID()

  @Override
  protected void setUp() {
    super.setUp()
    ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(project)).defaultCompiler = new GreclipseIdeaCompiler(project)
    def jarRoot = RepositoryTestLibrary.loadRoots(project, grEclipseArtifactID, GROOVY_PLUGIN_RELEASE).first().file
    GreclipseIdeaCompilerSettings.getSettings(project).greclipsePath = JarFileSystem.instance.getVirtualFileForJar(jarRoot).path
  }

  protected List<String> chunkRebuildMessage(String builder) {
    return []
  }
}
