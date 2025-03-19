// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler;

import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.TestLibrary;
import org.junit.AssumptionViolatedException;

public class GrEclipse2421Test extends GrEclipseTestBase {
  @Override
  protected TestLibrary getGroovyLibrary() {
    return GroovyProjectDescriptors.LIB_GROOVY_2_4;
  }

  @Override
  protected String getGrEclipseArtifactID() {
    return "org.codehaus.groovy:groovy-eclipse-batch:2.4.21-01";
  }

  @Override
  public void test_honor_bytecode_version() {
    throw new AssumptionViolatedException("Groovy-Eclipse 2.4 requires java 1.6 out which is missing");
  }
}
