/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.slicer

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class GroovySliceBackwardTest {
  private val slice by groovySliceFixture(groovyProject, true)

  @Test
  fun testSimple() = slice.doTest()

  @Test
  fun testGroovyJavaGroovy() = slice.doTest { listOf("$it.groovy", "$it.java") }

  @Test
  fun testJavaGroovyJava() = slice.doTest { listOf("$it.java", "$it.groovy") }

  companion object {
    private val groovyProject = lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR)
  }
}
