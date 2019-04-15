// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.compiler

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.JarFileSystem
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.RepositoryTestLibrary

@CompileStatic
class GrEclipse2416Test extends GrEclipseTestBase {

  @Override
  protected String getGrEclipsePath() {
    def jarRoot = RepositoryTestLibrary.loadRoots(project, "org.codehaus.groovy:groovy-eclipse-batch:2.4.16-01")[0].file
    return JarFileSystem.instance.getVirtualFileForJar(jarRoot).path
  }

  @Override
  protected void addGroovyLibrary(Module to) {
    GroovyProjectDescriptors.LIB_GROOVY_2_4.addTo(to)
  }
}
