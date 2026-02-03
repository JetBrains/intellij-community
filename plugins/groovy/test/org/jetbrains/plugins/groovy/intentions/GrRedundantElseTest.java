// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions;

import org.jetbrains.plugins.groovy.intentions.control.GrRedundantElseIntention;

/**
 * @author Max Medvedev
 */
public class GrRedundantElseTest extends GrIntentionTestCase {
  public GrRedundantElseTest() {
    super(GrRedundantElseIntention.HINT);
  }

  public void testBlock() {
    doTextTest("""
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

  public void testSingleStatement() {
    doTextTest("""
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

  public void testInsideIf() {
    doTextTest("""
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

  public void testInsideFor() {
    doTextTest("""
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

  public void testInsideFor2() {
    doTextTest("""
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

  public void testInsideFor3() {
    doAntiTest("""
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

  public void testInsideFor4() {
    doTextTest("""
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

  public void test_IDEA_281835() {
    doTextTest("""
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
