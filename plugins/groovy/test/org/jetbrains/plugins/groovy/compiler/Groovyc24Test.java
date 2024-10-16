// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.compiler

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.TestLibrary

@CompileStatic
class Groovyc24Test extends GroovycTestBase {

  @Override
  protected TestLibrary getGroovyLibrary() {
    GroovyProjectDescriptors.LIB_GROOVY_2_4
  }
}
