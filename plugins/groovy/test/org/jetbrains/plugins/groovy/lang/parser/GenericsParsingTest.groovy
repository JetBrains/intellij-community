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
class GenericsParsingTest extends GroovyParsingTestCase {
  final String basePath = super.basePath + "generics"

  void testErr1() throws Throwable { doTest() }

  void testErr2() throws Throwable { doTest() }

  void testErr3() throws Throwable { doTest() }

  void testErr4() { doTest() }

  void testGenmethod1() throws Throwable { doTest() }

  void testGenmethod2() throws Throwable { doTest() }

  void testGenmethod3() throws Throwable { doTest() }

  void testGenmethod4() throws Throwable { doTest() }

  void testGenmethod5() { doTest() }

  void testTypeargs1() throws Throwable { doTest() }

  void testTypeparam1() throws Throwable { doTest() }

  void testTypeparam2() throws Throwable { doTest() }

  void testTypeparam3() throws Throwable { doTest() }

}