/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser.groovy

import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslUtil.gradleNameFor
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.PlatformTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory

class GradleNameTest : PlatformTestCase() {
  fun gradleNameFromString(string : String) : String? {
    val psiFactory = GroovyPsiElementFactory.getInstance(myProject)
    val expression = psiFactory.createExpressionFromText(string)
    return gradleNameFor(expression)
  }

  fun testGradleName() {
    assertThat(gradleNameFromString("abc")).isEqualTo("abc")
    assertThat(gradleNameFromString("abc.def")).isEqualTo("abc.def")
    assertThat(gradleNameFromString("abc.def.foo")).isEqualTo("abc.def.foo")
    assertThat(gradleNameFromString("abc.def.foo.ghi")).isEqualTo("abc.def.foo.ghi")
    assertThat(gradleNameFromString("abc.def.foo.ext.bar")).isEqualTo("abc.def.foo.ext.bar")

    // escaping tests
    assertThat(gradleNameFromString("abc.def.'foo.'.ghi")).isEqualTo("abc.def.foo\\..ghi")
    assertThat(gradleNameFromString("abc.def.'foo.'.ext.'bar.'")).isEqualTo("abc.def.foo\\..ext.bar\\.")

    // unquoting tests
    assertThat(gradleNameFromString("abc.def.'foo'")).isEqualTo("abc.def.foo")
    assertThat(gradleNameFromString("abc.def.foo.'ghi.'")).isEqualTo("abc.def.foo.ghi\\.")
    assertThat(gradleNameFromString("abc.'def.'.foo.ghi")).isEqualTo("abc.def\\..foo.ghi")
    assertThat(gradleNameFromString("abc.def.foo.'ext'.bar")).isEqualTo("abc.def.foo.ext.bar")

    // Groovy has a lot of quoted identifier syntaxes
    assertThat(gradleNameFromString("abc.\"def\"")).isEqualTo("abc.def")
    assertThat(gradleNameFromString("abc.\"\"\"def\"\"\"")).isEqualTo("abc.def")
    assertThat(gradleNameFromString("abc.'''def'''")).isEqualTo("abc.def")
    assertThat(gradleNameFromString("abc./def/")).isEqualTo("abc.def")
    assertThat(gradleNameFromString("abc.$/def/$")).isEqualTo("abc.def")

    // indexing tests
    assertThat(gradleNameFromString("abc.def[0]")).isEqualTo("abc.def[0]")
    assertThat(gradleNameFromString("abc.'def.'[0]")).isEqualTo("abc.def\\.[0]")
  }

  fun testNullGradleName() {
    assertThat(gradleNameFromString("\"foo\"")).isNull()
    assertThat(gradleNameFromString("1")).isNull()
    assertThat(gradleNameFromString("[1, 2, 3]")).isNull()
    assertThat(gradleNameFromString("[1: 2, 2: 3]")).isNull()
    assertThat(gradleNameFromString("{ it -> it + 2 }")).isNull()
    assertThat(gradleNameFromString("foo()")).isNull()
    assertThat(gradleNameFromString("bar(\"foo\", \"baz\")")).isNull()
    assertThat(gradleNameFromString("abc.def.create(\"foo\")")).isNull()
    assertThat(gradleNameFromString("abc.def[0,1]")).isNull()
  }
}
