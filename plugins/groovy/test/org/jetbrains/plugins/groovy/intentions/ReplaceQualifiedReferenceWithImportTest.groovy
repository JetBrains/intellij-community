/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions

import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Max Medvedev
 */
public class ReplaceQualifiedReferenceWithImportTest extends GrIntentionTestCase {

  @Override
  protected String getBasePath() {
    "${TestUtils.testDataPath}intentions/replaceQualifiedReferenceWithImport/"
  }

  private doTest(boolean intentionExists) {
    myFixture.addClass('package p1; public class X{}')
    myFixture.addClass('package p2; public class X{}')
    doTest(GroovyIntentionsBundle.message('replace.qualified.reference.with.import.intention.name'), intentionExists)
  }

  void testSimple() {
    doTest(true)
  }

  void testCan() {
    doTest(true)
  }

  void testCannot1() {
    doTest(false)
  }

  void testCannot2() {
    doTest(false)
  }
}
