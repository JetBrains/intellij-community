// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.ResolveTest;
import org.junit.Test;

public class ResolvePropertyVersusTest extends GroovyLatestTest implements ResolveTest {
  @Test
  public void instanceGetterVsInstanceField() {
    getFixture().addFileToProject("classes.groovy", """
      class C {
        public foo = "field"
        def getFoo() { "getter" }
      }
      """);
    resolveTest("new C().<caret>foo", GrMethod.class);
    resolveTest("C.<caret>foo", GrField.class);
  }

  @Test
  public void staticGetterVsStaticField() {
    getFixture().addFileToProject("classes.groovy", """
      class C {
        public static foo = "field"
        static def getFoo() { "getter" }
      }
      """);
    resolveTest("new C().<caret>foo", GrMethod.class);
    resolveTest("C.<caret>foo", GrMethod.class);
  }

  @Test
  public void instanceGetterVsStaticField() {
    getFixture().addFileToProject("classes.groovy", """
      class C {
        public static foo = "field"
        def getFoo() { "getter" }
      }
      """);
    resolveTest("new C().<caret>foo", GrMethod.class);
    resolveTest("C.<caret>foo", GrField.class);
  }

  @Test
  public void staticGetterVsInstanceField() {
    getFixture().addFileToProject("classes.groovy", """
      class C {
        public foo = "field"
        static def getFoo() { "getter" }
      }
      """);
    resolveTest("new C().<caret>foo", GrMethod.class);
    resolveTest("C.<caret>foo", GrMethod.class);
  }
}
