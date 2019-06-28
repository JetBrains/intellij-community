// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.compiler

import com.intellij.openapi.module.Module
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

@CompileStatic
class GrEclipse2416Test extends GrEclipseTestBase {

  @Override
  protected String getGrEclipseArtifactID() {
    "org.codehaus.groovy:groovy-eclipse-batch:2.4.16-01"
  }

  @Override
  protected void addGroovyLibrary(Module to) {
    GroovyProjectDescriptors.LIB_GROOVY_2_4.addTo(to)
  }
}
