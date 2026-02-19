// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.CompilerConfiguration;
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants;
import org.jetbrains.jps.incremental.groovy.JpsGroovycRunner;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.TestLibrary;

public final class Groovyc30InProcessWithClassLoaderResolvingTest extends Groovyc25Test {
  @Override
  protected TestLibrary getGroovyLibrary() {
    return GroovyProjectDescriptors.LIB_GROOVY_3_0;
  }

  @Override
  protected boolean isRebuildExpectedAfterChangeInJavaClassExtendedByGroovy() {
    return false;
  }

  @Override
  protected boolean isRebuildExpectedAfterChangesInGroovyWhichUseJava() {
    return true;
  }

  @Override
  protected boolean isRebuildExpectedWhileCompilingDependentTrait() {
    return false;
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
}
