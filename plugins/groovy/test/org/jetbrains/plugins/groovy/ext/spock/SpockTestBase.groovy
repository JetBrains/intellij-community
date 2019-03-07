// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.spock

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LibraryLightProjectDescriptor
import org.jetbrains.plugins.groovy.RepositoryTestLibrary
import org.jetbrains.plugins.groovy.util.LightProjectTest

abstract class SpockTestBase extends LightProjectTest {

  private static final LightProjectDescriptor SPOCK_PROJECT = new LibraryLightProjectDescriptor(
    GroovyProjectDescriptors.LIB_GROOVY_2_4 + new RepositoryTestLibrary("org.spockframework:spock-core:1.2-groovy-2.4")
  )

  @Override
  final LightProjectDescriptor getProjectDescriptor() {
    SPOCK_PROJECT
  }
}
