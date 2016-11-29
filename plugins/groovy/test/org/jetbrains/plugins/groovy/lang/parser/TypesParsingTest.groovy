/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.parser

/**
 * @author peter
 */
class TypesParsingTest extends GroovyParsingTestCase {
  @Override
  String getBasePath() {
    return super.basePath + "types"
  }

  void testAnn_def1() throws Throwable { doTest() }

  void testAnn_def2() throws Throwable { doTest() }

  void testAnn_def3() throws Throwable { doTest() }

  void testDefault1() throws Throwable { doTest() }

  void testDefault2() throws Throwable { doTest() }

  void testType1() throws Throwable { doTest() }

  void testType10() throws Throwable { doTest() }

  void testType11() throws Throwable { doTest() }

  void testType12() throws Throwable { doTest() }

  void testType13() throws Throwable { doTest() }

  void testType14() throws Throwable { doTest() }

  void testType15() throws Throwable { doTest() }

  void testType2() throws Throwable { doTest() }

  void testType3() throws Throwable { doTest() }

  void testType4() throws Throwable { doTest() }

  void testType5() throws Throwable { doTest() }

  void testType6() throws Throwable { doTest() }

  void testType7() throws Throwable { doTest() }

  void testType8() throws Throwable { doTest() }

  void testType9() throws Throwable { doTest() }

  void testInnerEnum() throws Throwable { doTest() }

  void testNewlineBeforeClassBrace() throws Throwable { doTest() }

  void testNewlineBeforeExtends() throws Throwable { doTest() }

  void testStaticInitializer() throws Throwable { doTest() }

  void testInterfaceWithGroovyDoc() throws Throwable { doTest() }

  void testIncorrectParam1() { doTest() }

  void testIncorrectParameter2() { doTest() }

  void testIncorrectParam3() { doTest() }

  void testEmptyTypeArgs() { doTest() }
}