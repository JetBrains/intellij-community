// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.compiler

import groovy.transform.CompileStatic

@CompileStatic
class GrEclipse2415Test extends GrEclipseTestBase {

  @Override
  protected String getGrEclipseArtifactID() {
    "org.codehaus.groovy:groovy-eclipse-batch:2.4.15-01"
  }
}
