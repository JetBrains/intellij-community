// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.TestLibrary
import org.junit.AssumptionViolatedException

@CompileStatic
class GrEclipse2421Test extends GrEclipseTestBase {

  @Override
  protected TestLibrary getGroovyLibrary() {
    GroovyProjectDescriptors.LIB_GROOVY_2_4
  }

  @Override
  protected String getGrEclipseArtifactID() {
    "org.codehaus.groovy:groovy-eclipse-batch:2.4.21-01"
  }

  void "test honor bytecode version"() {
    throw new AssumptionViolatedException("Groovy-Eclipse 2.4 requires java 1.6 out which is missing")
  }
}
