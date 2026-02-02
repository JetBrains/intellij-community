// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import org.jetbrains.jps.incremental.groovy.JpsGroovycRunner;

import java.util.List;

public final class GroovycForkedJava8Test extends Groovyc25Test {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CompilerConfiguration.getInstance(getProject()).setAdditionalOptions(List.of("-Xlint:-options"));
  }

  @Override
  protected JavaSdkVersion getJdkVersion() {
    return JavaSdkVersion.JDK_1_8;
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder<?> moduleBuilder) throws Exception {
    super.tuneFixture(moduleBuilder);
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8);
  }

  public void testForkedGroovycWithOptimizedClasspath() {
    myFixture.addFileToProject("GroovyFile.groovy", "");
    CompilerConfiguration.getInstance(getProject()).setBuildProcessVMOptions(
      " -D" + JpsGroovycRunner.GROOVYC_IN_PROCESS + "=false" +
      " -D" + JpsGroovycRunner.GROOVYC_CLASSPATH_OPTIMIZE_THRESHOLD + "=1");
    assertEmpty(make());
  }
}
