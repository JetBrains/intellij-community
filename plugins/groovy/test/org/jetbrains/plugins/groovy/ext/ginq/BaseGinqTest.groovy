// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LibraryLightProjectDescriptor
import org.jetbrains.plugins.groovy.RepositoryTestLibrary
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroRegistryService

abstract class BaseGinqTest extends GrHighlightingTestBase {

  private static final RepositoryTestLibrary LIB_GINQ = new RepositoryTestLibrary("org.apache.groovy:groovy-ginq:4.0.0")

  private static final LightProjectDescriptor GROOVY_4_0_WITH_GINQ =
    new LibraryLightProjectDescriptor(GroovyProjectDescriptors.LIB_GROOVY_4_0 + LIB_GINQ) {
      @Override
      Sdk getSdk() {
        return JavaSdk.getInstance().createJdk("TEST_JDK", IdeaTestUtil.requireRealJdkHome(), false)
      }
    }

  final LightProjectDescriptor projectDescriptor = GROOVY_4_0_WITH_GINQ

  @Override
  void setUp() throws Exception {
    super.setUp()
    myFixture.project.getService(GroovyMacroRegistryService).refreshModule(module)
  }
}
