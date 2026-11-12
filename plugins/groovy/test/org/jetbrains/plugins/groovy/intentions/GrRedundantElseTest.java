// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.intentions.control.GrRedundantElseIntention;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrRedundantElseTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention = grIntentionFixture(projectFixture, GrRedundantElseIntention.HINT, null);

  @Test
  public void testBlock() {
    grIntention.get().doTextTest("""
                 def foo() {
                     if (cond) {
                         print 2
                         return
                     }
                     e<caret>lse {
                         print 3
                         print 4
                     }
                 }
                 """, """
                 def foo() {
                     if (cond) {
                         print 2
                         return
                     }
                     <caret>print 3
                         print 4
                 }
                 """);
  }

  @Test
  public void testSingleStatement() {
    grIntention.get().doTextTest("""
                 def foo() {
                     if (cond) {
                         print 2
                         return
                     }
                     e<caret>lse print 3
                 }
                 """, """
                 def foo() {
                     if (cond) {
                         print 2
                         return
                     }
                     <caret>print 3
                 }
                 """);
  }

  @Test
  public void testInsideIf() {
    grIntention.get().doTextTest("""
                 def foo() {
                     if (abc)
                         if (cond) {
                             print 2
                             return
                         }
                         e<caret>lse print 3
                 }
                 """, """
                 def foo() {
                     if (abc) {
                         if (cond) {
                             print 2
                             return
                         }
                         <caret>print 3
                     }
                 }
                 """);
  }

  @Test
  public void testInsideFor() {
    grIntention.get().doTextTest("""
                 def foo() {
                     for (i in 1..2) {
                         if (cond) {
                             print 2
                             break
                         }
                         e<caret>lse {
                             print 3
                         }
                     }
                 }
                 """, """
                 def foo() {
                     for (i in 1..2) {
                         if (cond) {
                             print 2
                             break
                         }
                         <caret>print 3
                     }
                 }
                 """);
  }

  @Test
  public void testInsideFor2() {
    grIntention.get().doTextTest("""
                 def foo() {
                     for (i in 1..2) {
                         if (cond) {
                             print 2
                             if (abc) {
                                 break
                             }
                             else {
                                 continue
                             }
                         }
                         e<caret>lse {
                             print 3
                         }
                     }
                 }
                 """, """
                 def foo() {
                     for (i in 1..2) {
                         if (cond) {
                             print 2
                             if (abc) {
                                 break
                             }
                             else {
                                 continue
                             }
                         }
                         <caret>print 3
                     }
                 }
                 """);
  }

  @Test
  public void testInsideFor3() {
    grIntention.get().doAntiTest("""
                 def foo() {
                     for (i in 1..2) {
                         if (cond) {
                             print 2
                         }
                         e<caret>lse {
                             print 3
                         }
                     }
                 }
                 """);
  }

  @Test
  public void testInsideFor4() {
    grIntention.get().doTextTest("""
                 def foo() {
                     for (i in 1..2) {
                         if (cond) {
                             print 2
                             if (abc) {
                                 break
                             }
                             else {
                                 continue
                             }
                         }
                         e<caret>lse {
                             print 3
                             break
                         }
                     }
                 }
                 """, """
                 def foo() {
                     for (i in 1..2) {
                         if (cond) {
                             print 2
                             if (abc) {
                                 break
                             }
                             else {
                                 continue
                             }
                         }
                         <caret>print 3
                             break
                     }
                 }
                 """);
  }

  @Test
  public void test_IDEA_281835() {
    grIntention.get().doTextTest("""
                 def foo() {
                     if (true) {
                         return x
                     } el<caret>se {
                 
                     }
                 }""", """
                 def foo() {
                     if (true) {
                         return x
                     }
                 }""");
  }
}
