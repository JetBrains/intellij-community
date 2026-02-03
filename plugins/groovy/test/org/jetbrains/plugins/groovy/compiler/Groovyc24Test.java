// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.compiler;

import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.TestLibrary;

public class Groovyc24Test extends GroovycTestBase {
  @Override
  protected TestLibrary getGroovyLibrary() {
    return GroovyProjectDescriptors.LIB_GROOVY_2_4;
  }
}
