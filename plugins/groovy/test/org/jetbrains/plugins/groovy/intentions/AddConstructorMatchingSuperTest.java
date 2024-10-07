// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * @author Max Medvedev
 */
public class AddConstructorMatchingSuperTest extends GrIntentionTestCase {
  private static final String HINT = "Create constructor matching super";

  public AddConstructorMatchingSuperTest() {
    super(HINT);
  }

  public void testGroovyToGroovy() {
    doTextTest(
      """
        @interface Anno {}
        class Base {
            Base(int p, @Anno int x) throws Exception {}
        }
        
        class Derived exten<caret>ds Base {
        }
        """,
      """
        @interface Anno {}
        class Base {
            Base(int p, @Anno int x) throws Exception {}
        }
        
        class Derived extends Base {
            <caret>Derived(int p, int x) throws Exception {
                super(p, x)
            }
        }
        """);
  }

  public void testJavaToGroovy() {
    myFixture.addClass("""
                         @interface Anno {}
                         class Base {
                             Base(int p, @Anno int x) throws Exception {}
                         }
                         """);
    doTextTest("""
                 class Derived exten<caret>ds Base {
                 }
                 """,
               """
                 class Derived extends Base {
                     <caret>Derived(int p, int x) throws Exception {
                         super(p, x)
                     }
                 }
                 """);
  }

  public void testGroovyToJava() {
    myFixture.addClass("""
                         class Base {
                             Base(int p, @Override int x) throws Exception {}
                         }
                         """);
    myFixture.configureByText("a.java", """
      class Derived exten<caret>ds Base {
      }
      """);
    final List<IntentionAction> list = myFixture.filterAvailableIntentions(HINT);
    myFixture.launchAction(UsefulTestCase.assertOneElement(list));
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.checkResult(
      """
        class Derived extends Base {
            <caret>Derived(int p, int x) throws Exception {
                super(p, x);
            }
        }
        """);
  }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/constructorMatchingSuper/";
  }
}
