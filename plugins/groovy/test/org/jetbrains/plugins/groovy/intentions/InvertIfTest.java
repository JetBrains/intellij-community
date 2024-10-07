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
package org.jetbrains.plugins.groovy.intentions;

/**
 * @author Niels Harremoes
 */
public class InvertIfTest extends GrIntentionTestCase {
  public InvertIfTest() {
    super(GroovyIntentionsBundle.message("invert.if.intention.name"));
  }

  public void testDoNotTriggerOnIncompleteIf() {
    doAntiTest("""
                 i<caret>f () {
                   succes
                 } else {
                   no_succes
                 }
                 """);
  }

  public void testSimpleCondition() {
    doTextTest("""
                 i<caret>f (a) {
                     succes
                 } else {
                     no_succes
                 }
                 """, """
                 i<caret>f (!a) {
                     no_succes
                 } else {
                     succes
                 }
                 """);
  }

  public void testCallCondition() {

    doTextTest("""
                 i<caret>f (func()) {
                     succes
                 } else {
                     no_succes
                 }
                 """, """
                 i<caret>f (!func()) {
                     no_succes
                 } else {
                     succes
                 }
                 """);
  }

  public void testComplexCondition() {
    doTextTest("""
                 i<caret>f (a && b) {
                     succes
                 } else {
                     no_succes
                 }
                 """, """
                 i<caret>f (!(a && b)) {
                     no_succes
                 } else {
                     succes
                 }
                 """);
  }

  public void testNegatedComplexCondition() {
    doTextTest("""
                 i<caret>f (!(a && b)) {
                     succes
                 } else {
                     no_succes
                 }
                 """, """
                 i<caret>f (a && b) {
                     no_succes
                 } else {
                     succes
                 }
                 """);
  }

  public void testNegatedSimpleCondition() {
    doTextTest("""
                 i<caret>f (!a) {
                     succes
                 } else {
                     no_succes
                 }
                 """, """
                 i<caret>f (a) {
                     no_succes
                 } else {
                     succes
                 }
                 """);
  }

  public void testNoElseBlock() {
    doTextTest("""
                 i<caret>f (a) {
                     succes
                 }
                 nosuccess
                 """, """
                 i<caret>f (!a) {
                 } else {
                     succes
                 }
                 nosuccess
                 """);
  }

  public void testEmptyThenBlockIsRemoved() {
    doTextTest("""
                 i<caret>f (a) {
                 } else {
                     no_succes
                 }
                 """, """
                 i<caret>f (!a) {
                     no_succes
                 }
                 """);
  }

  public void testContinue() {
    doTextTest("""
                 for (i in []) {
                     i<caret>f (2) {
                         print 2
                         continue
                     }
                 
                     print 3
                     print(3)
                 }
                 """, """
                 for (i in []) {
                     if (!(2)) {
                 
                         print 3
                         print(3)
                     } else {
                         print 2
                         continue
                     }
                 }
                 """);
  }
}
