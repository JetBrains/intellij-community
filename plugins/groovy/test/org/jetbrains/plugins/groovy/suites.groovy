// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy

import org.jetbrains.plugins.groovy.util.AllTestsSuite
import org.jetbrains.plugins.groovy.util.SlowPolicy
import org.jetbrains.plugins.groovy.util.TestPackage
import org.junit.runner.RunWith

@RunWith(AllTestsSuite.class)
@TestPackage("org.jetbrains.plugins.groovy")
class FastGroovyTestSuite {}

@RunWith(AllTestsSuite.class)
@TestPackage(value = "org.jetbrains.plugins.groovy", policy = SlowPolicy.SLOW_ONLY)
class SlowGroovyTestSuite {}

@RunWith(AllTestsSuite.class)
@TestPackage(value = "org.jetbrains.plugins.groovy", policy = SlowPolicy.ALL)
class AllGroovyTestSuite {}

@RunWith(AllTestsSuite.class)
@TestPackage(value = "org.jetbrains.plugins.groovy.lang.parser")
class GroovyParserTestSuite {}
