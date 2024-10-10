// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ide;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.junit.Assert;

public class GroovyRunLineMarkerTest extends LightGroovyTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    Registry.get("ide.jvm.run.marker").setValue(true, getTestRootDisposable());
  }

  @Override
  @NotNull
  public final LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  public void testSimple() {
    getFixture().configureByText("_.groovy", """
      class MainTest {
        static void <caret>foo(String[] args) {}
        static void main(String[] args) {}
      }
      """);
    Assert.assertTrue(getFixture().findGuttersAtCaret().isEmpty());
    Assert.assertEquals(2, getFixture().findAllGutters().size());
  }

  public void testImplicitStringArray() {
    getFixture().configureByText("_.groovy", """
      class MainTest {
        static void main(args) {}
      }
      """);
    assert getFixture().findAllGutters().size() == 2;
  }

  public void testJavaClassInheritor() {
    getFixture().addClass("""
                              class Main {
                              public static void main(String[] args){}
                            }
                            """);
    getFixture().configureByText("_.groovy", """
      class MainTest extends Main {}
      """);
    Assert.assertEquals(1, getFixture().findAllGutters().size());
  }

  public void testDefaultParameters() {
    getFixture().configureByText("_.groovy", """
      class MainTest {
        static void main(String[] args, b = 2) {}
      }
      """);
    Assert.assertEquals(2, getFixture().findAllGutters().size());
  }
}
