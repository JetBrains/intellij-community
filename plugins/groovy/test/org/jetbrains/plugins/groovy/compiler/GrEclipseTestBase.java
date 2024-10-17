// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.JUnit38AssumeSupportRunner;
import org.jetbrains.plugins.groovy.RepositoryTestLibrary;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit38AssumeSupportRunner.class)
public abstract class GrEclipseTestBase extends GroovyCompilerTest {
  protected abstract String getGrEclipseArtifactID();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(getProject())).setDefaultCompiler(
      new GreclipseIdeaCompiler(getProject()));
    VirtualFile jarRoot = RepositoryTestLibrary.loadRoots(getProject(), getGrEclipseArtifactID(), GROOVY_PLUGIN_RELEASE)
      .iterator().next().getFile();
    GreclipseIdeaCompilerSettings.getSettings(getProject()).greclipsePath =
      JarFileSystem.getInstance().getVirtualFileForJar(jarRoot).getPath();
  }

  protected List<String> chunkRebuildMessage(String builder) {
    return new ArrayList<>();
  }

  public static final RemoteRepositoryDescription GROOVY_PLUGIN_RELEASE =
    new RemoteRepositoryDescription("groovy.plugin.release", "JFrog Groovy Plugin Release repository",
                                    "https://groovy.jfrog.io/artifactory/plugins-release-local/");
}
