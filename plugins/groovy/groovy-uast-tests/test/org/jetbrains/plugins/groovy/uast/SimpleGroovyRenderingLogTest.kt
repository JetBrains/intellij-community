// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.uast

import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.RenderLogTestBase
import org.junit.Test


class SimpleGroovyRenderingLogTest : AbstractGroovyRenderLogTest(), RenderLogTestBase {

  override fun check(testName: String, file: UFile) {
    super<RenderLogTestBase>.check(testName, file)
  }

  @Test
  fun testSimple() = doTest("SimpleClass.groovy")

  @Test
  fun testClassWithInners() = doTest("ClassWithInners.groovy")

  @Test
  fun testAnnotations() = doTest("Annotations.groovy")

}