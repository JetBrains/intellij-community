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
package org.jetbrains.plugins.groovy.lang.parser;

/**
 * @author peter
 */
public class GenericsParsingTest extends GroovyParsingTestCase {
  final String basePath = super.basePath + "generics"

  public void testErr1() throws Throwable { doTest(); }
  public void testErr2() throws Throwable { doTest(); }
  public void testErr3() throws Throwable { doTest(); }
  public void testErr4() { doTest() }
  public void testGenmethod1() throws Throwable { doTest(); }
  public void testGenmethod2() throws Throwable { doTest(); }
  public void testGenmethod3() throws Throwable { doTest(); }
  public void testGenmethod4() throws Throwable { doTest(); }
  public void testGenmethod5() { doTest() }
  public void testTypeargs1() throws Throwable { doTest(); }
  public void testTypeparam1() throws Throwable { doTest(); }
  public void testTypeparam2() throws Throwable { doTest(); }
  public void testTypeparam3() throws Throwable { doTest(); }

}