/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
public class AnnotationsParsingTest extends GroovyParsingTestCase {
  final String basePath = super.basePath + "annotations"

  public void testAnn1() { doTest() }
  public void testAnn2() { doTest() }
  public void testAnn3() { doTest() }
  public void testAnn4() { doTest() }
  public void testAnn5() { doTest() }
  public void testAnn6() { doTest() }
  public void testAnn7() { doTest() }
  public void testClassLiteral() { doTest() }
  public void testImportAnn() { doTest() }
  public void testPackageAnn() { doTest() }
  public void testDefAttribute() {doTest()}
  public void testLineFeedAfterRef() {doTest()}
  public void testKeywordsAttributes() {doTest()}
  public void testMess() { doTest() }
}
