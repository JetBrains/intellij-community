// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.singleton;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.junit.Assert;

public class SingletonConstructorInspectionTest extends LightGroovyTestCase {
  @Override
  @NotNull
  public final LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getFixture().enableInspections(SingletonConstructorInspection.class);
  }

  public void testHighlighting() {
    myFixture.configureByText("_.groovy", """
        @Singleton
        class A {
            <error descr="@Singleton class should not have constructors">A</error>() {}
            <error descr="@Singleton class should not have constructors">A</error>(a) {}
        }
        
        @Singleton(strict=true)
        class ExplicitStrict {
          <error descr="@Singleton class should not have constructors">ExplicitStrict</error>() {}
          <error descr="@Singleton class should not have constructors">ExplicitStrict</error>(a) {}
        }
        
        @Singleton(strict = false)
        class NonStrict {
          NonStrict() {}
          NonStrict(a) {}
        }
        """);

    myFixture.checkHighlighting();
  }

  public void testMakeNonStrictFix() {
    myFixture.configureByText("_.groovy", """
        @Singleton
        class A {
          <caret>A() {}
        }
        """);

    IntentionAction intention = myFixture.findSingleIntention(GroovyBundle.message("singleton.constructor.makeNonStrict"));
    Assert.assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult("""
                    @Singleton(strict = false)
                    class A {
                      <caret>A() {}
                    }
                    """);
  }

  public void testMakeNonStrictFixExisting() {
    myFixture.configureByText("_.groovy", """
        @Singleton(strict = true, property = "lol")
        class A {
          <caret>A() {}
        }
        """);

    IntentionAction intention = myFixture.findSingleIntention(GroovyBundle.message("singleton.constructor.makeNonStrict"));
    Assert.assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult("""
                    @Singleton(strict = false, property = "lol")
                    class A {
                      <caret>A() {}
                    }
                    """);
  }

  public void testRemoveConstructorFix() {
    myFixture.configureByText("_.groovy", """
        @Singleton
        class A {
          <caret>A() {}
        }
        """);

    IntentionAction intention = myFixture.findSingleIntention(GroovyBundle.message("singleton.constructor.remove"));
    Assert.assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult("""
                    @Singleton
                    class A {
                    }
                    """);
  }
}
