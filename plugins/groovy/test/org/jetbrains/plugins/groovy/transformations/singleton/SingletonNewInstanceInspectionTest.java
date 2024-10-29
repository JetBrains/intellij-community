// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.singleton;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.bugs.NewInstanceOfSingletonInspection;
import org.junit.Assert;

public class SingletonNewInstanceInspectionTest extends LightGroovyTestCase {
  @Override
  @NotNull
  public final LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getFixture().enableInspections(NewInstanceOfSingletonInspection.class);
  }

  private void doTest(final String before, final String after) {
    myFixture.configureByText("_.groovy", before);
    IntentionAction action = myFixture.findSingleIntention(GroovyBundle.message("replace.new.expression.with.instance.access"));
    Assert.assertNotNull(action);
    myFixture.launchAction(action);
    myFixture.checkResult(after);
  }

  public void testFixSimple() {
    doTest("""
           @Singleton
           class A {}
           
           new <caret>A()
           """, """
           @Singleton
           class A {}
           
           A.instance
           """);
  }

  public void testFixCustomPropertyName() {
    doTest("""
           @Singleton(property = "coolInstance")
           class A {}
           
           new <caret>A()
           """, """
           @Singleton(property = "coolInstance")
           class A {}
           
           A.coolInstance
           """);
  }
}
