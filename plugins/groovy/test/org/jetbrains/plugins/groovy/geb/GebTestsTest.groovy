/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.geb

import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Sergey Evdokimov
 */
class GebTestsTest extends AbstractGebLightTestCase {

  void testSpockTestMemberCompletion() {
    myFixture.configureByText("FooTest.groovy", """
class FooTest extends geb.spock.GebReportingSpec {
    def testFoo() {
      when:
      <caret>
    }
}
""")

    TestUtils.checkCompletionContains(myFixture, "\$()", "to()", "go()", "currentWindow", "verifyAt()", "title")
  }

  void testJUnitTestMemberCompletion() {
    myFixture.configureByText("FooTest.groovy", """
class FooTest extends geb.junit4.GebReportingTest {
    def testFoo() {
      <caret>
    }
}
""")

    TestUtils.checkCompletionContains(myFixture, "\$()", "to()", "go()", "currentWindow", "verifyAt()", "title")
  }

  void testFieldNameCompletion() {
    myFixture.configureByText("FooTest.groovy", """
class FooTest extends geb.Page {

    static <caret>

    static content = {}
}
""")

    TestUtils.checkCompletionContains(myFixture, "at", "url")
    assert !myFixture.getLookupElementStrings().contains("content")
  }

}
