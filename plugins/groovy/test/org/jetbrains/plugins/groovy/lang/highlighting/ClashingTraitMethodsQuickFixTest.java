// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.confusing.ClashingTraitMethodsInspection;
import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase;

/**
 * Created by Max Medvedev on 09/06/14
 */
public class ClashingTraitMethodsQuickFixTest extends GrIntentionTestCase {
  public ClashingTraitMethodsQuickFixTest() {
    super(GroovyBundle.message("declare.explicit.implementations.of.trait"), ClashingTraitMethodsInspection.class);
  }

  /**
   * Somewhat incorrect, the overriding method return type should be `def` instead of `Object`
   */
  public void testQuickFix() {
    doTextTest("""
                 trait T1 {
                     def foo(){}
                 }

                 trait T2 {
                     def foo(){}
                 }

                 class <caret>A implements T1, T2 {

                 }
                 """, """
                 trait T1 {
                     def foo(){}
                 }

                 trait T2 {
                     def foo(){}
                 }

                 class A implements T1, T2 {

                     @Override
                     Object foo() {
                         return T2.super.foo()
                     }
                 }
                 """);
  }
}
