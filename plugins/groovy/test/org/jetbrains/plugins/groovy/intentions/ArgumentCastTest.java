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
package org.jetbrains.plugins.groovy.intentions;

import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;

/**
 * @author Max Medvedev
 */
public class ArgumentCastTest extends GrIntentionTestCase {
  public void test0() {
    doTextTest("""
                 def foo(char c) {}
                 foo(<caret>'a')
                 """, "Cast 1st parameter to char", """
                 def foo(char c) {}
                 foo('a' as char)
                 """, GroovyAssignabilityCheckInspection.class);
  }

  public void test1() {
    doTextTest("""
                 def foo(char c, int x) {}
                 foo(<caret>'a', 2)
                 """, "Cast 1st parameter to char", """
                 def foo(char c, int x) {}
                 foo('a' as char, 2)
                 """, GroovyAssignabilityCheckInspection.class);
  }

  public void test2() {
    doAntiTest("""
                 def foo(char c, int x) {}
                 
                 foo(<caret>'a', 2, 3)
                 """, "Cast 1st parameter to char", GroovyAssignabilityCheckInspection.class);
  }

  public void test3() {
    doTextTest("""
                 def foo(char c, int x) {}
                 
                 foo(<caret>'a', 'a')
                 """, "Cast 1st parameter to char", """
                 def foo(char c, int x) {}
                 
                 foo('a' as char, 'a')
                 """, GroovyAssignabilityCheckInspection.class);
  }

  public void test3_2() {
    doTextTest("""
                 def foo(char c, int x) {}
                 
                 foo(<caret>'a', 'a')
                 """, "Cast 2nd parameter to int", """
                 def foo(char c, int x) {}
                 
                 foo('a', 'a' as int)
                 """, GroovyAssignabilityCheckInspection.class);
  }

  public void testMapArgIsIncorrect() {
    doAntiTest("""
                 void foo(Map<int, int> m, String o) {}
             
             
                 foo('',<caret> g:4 )
                 """, "Cast", GroovyAssignabilityCheckInspection.class);
  }

  public void ignoredestNamedArguments() {
    doTextTest("""
                 
                 def foo(a, int b, c) {}
                 foo(<caret>'a', b: 1, 1)
                 """, "Cast 1st parameter to int", """
                 
                 def foo(a, int b, c) {}
                 foo('a' as int, b: 1, 1)
                 """, GroovyAssignabilityCheckInspection.class);
  }
}
