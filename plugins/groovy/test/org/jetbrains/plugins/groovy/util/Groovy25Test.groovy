// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

@CompileStatic
abstract class Groovy25Test extends LightProjectTest {

  @Override
  final LightProjectDescriptor getProjectDescriptor() {
    GroovyProjectDescriptors.GROOVY_2_5
  }
}
