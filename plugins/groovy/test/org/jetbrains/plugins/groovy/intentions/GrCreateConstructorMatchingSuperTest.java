// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions;

/**
 * @author Max Medvedev
 */
public class GrCreateConstructorMatchingSuperTest extends GrIntentionTestCase {
  public GrCreateConstructorMatchingSuperTest() {
    super("Create constructor matching super");
  }

  public void testSuperWithJavaDoc() {
    myFixture.addClass("""
                         public class Base {
                             /**
                              * my doc
                              */
                              public Base(int x) {
                              }
                         }
                         """);

    doTextTest("""
                 class <caret>Inheritor extends Base {
                 }
                 """,
               """
                 class Inheritor extends Base {
                     /**
                      * my doc
                      */
                     Inheritor(int x) {
                         super(x)
                     }
                 }
                 """);
  }

  public void testInvalidOriginalParameterName() {
    myFixture.addClass("""
                         class Base {
                           Base(String in, String s) {
                           }
                         }""");

    doTextTest("""
                 class <caret>Inh extends Base {
                 }
                 """,
               """
                 class Inh extends Base {
                     Inh(String s, String s2) {
                         super(s, s2)
                     }
                 }
                 """);
  }
}
