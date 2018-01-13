/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.uast

import org.junit.Test


class SimpleGroovyRenderingLogTest : AbstractGroovyRenderLogTest() {

  @Test
  fun testSimple() = doTest("SimpleClass.groovy")

  @Test
  fun testClassWithInners() = doTest("ClassWithInners.groovy")

}