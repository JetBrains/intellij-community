// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.uast

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.platform.uast.testFramework.common.RenderLogTestBase
import com.intellij.platform.uast.testFramework.env.AbstractUastFixtureTest

abstract class AbstractGroovyUastTest : AbstractUastFixtureTest() {
  public override fun getTestDataPath(): String =
    PathManagerEx.getCommunityHomePath() + "/plugins/groovy/groovy-uast-tests/testData"
}

abstract class AbstractGroovyRenderLogTest : AbstractGroovyUastTest(), RenderLogTestBase