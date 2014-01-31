/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.parser;

/**
 * @author peter
 */
public class TypesParsingTest extends GroovyParsingTestCase {
  @Override
  String getBasePath() {
    return super.basePath + "types";
  }

  public void testAnn_def1() throws Throwable { doTest(); }
  public void testAnn_def2() throws Throwable { doTest(); }
  public void testAnn_def3() throws Throwable { doTest(); }
  public void testDefault1() throws Throwable { doTest(); }
  public void testDefault2() throws Throwable { doTest(); }
  public void testType1() throws Throwable { doTest(); }
  public void testType10() throws Throwable { doTest(); }
  public void testType11() throws Throwable { doTest(); }
  public void testType12() throws Throwable { doTest(); }
  public void testType13() throws Throwable { doTest(); }
  public void testType14() throws Throwable { doTest(); }
  public void testType15() throws Throwable { doTest(); }
  public void testType2() throws Throwable { doTest(); }
  public void testType3() throws Throwable { doTest(); }
  public void testType4() throws Throwable { doTest(); }
  public void testType5() throws Throwable { doTest(); }
  public void testType6() throws Throwable { doTest(); }
  public void testType7() throws Throwable { doTest(); }
  public void testType8() throws Throwable { doTest(); }
  public void testType9() throws Throwable { doTest(); }

  public void testInnerEnum() throws Throwable { doTest(); }
  public void testNewlineBeforeClassBrace() throws Throwable { doTest(); }
  public void testNewlineBeforeExtends() throws Throwable { doTest(); }
  public void testStaticInitializer() throws Throwable { doTest(); }
  public void testInterfaceWithGroovyDoc() throws Throwable { doTest(); }

  public void testIncorrectParam1() { doTest() }
  public void testIncorrectParameter2() { doTest() }
  public void testIncorrectParam3() { doTest() }

  public void testEmptyTypeArgs() {doTest()}
}