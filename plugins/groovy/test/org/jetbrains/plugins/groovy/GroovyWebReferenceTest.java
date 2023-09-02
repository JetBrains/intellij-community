// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy

import com.intellij.openapi.paths.WebReference
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic

import static com.intellij.testFramework.fixtures.InjectionTestFixtureKt.assertInjectedReference

@CompileStatic
class GroovyWebReferenceTest extends LightGroovyTestCase {
  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  void 'test web reference in strings'() {
    myFixture.configureByText("Demo.groovy", """
      class Demo {
        static void main(String[] args) {
          def doubleQuotes = "http://double:8080/app"
          def singleQuotes = 'http://single:8080/app'
          
          def multilineSingle = '''
              https://multiline-single:8080/app
          '''
          
          def multilineDouble = \"\"\"
              http://multiline-double:8080/app
          \"\"\"
        }
      }
    """)

    assertInjectedReference(myFixture, WebReference.class, "http://double:8080/app")
    assertInjectedReference(myFixture, WebReference.class, "http://single:8080/app")
    assertInjectedReference(myFixture, WebReference.class, "https://multiline-single:8080/app")
    assertInjectedReference(myFixture, WebReference.class, "http://multiline-double:8080/app")
  }
}
