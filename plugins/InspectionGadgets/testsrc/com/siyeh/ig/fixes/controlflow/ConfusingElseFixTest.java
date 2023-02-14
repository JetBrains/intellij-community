// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.ConfusingElseInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class ConfusingElseFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new ConfusingElseInspection();
  }

  public void testRemoveElse() {
    doMemberTest(InspectionGadgetsBundle.message("redundant.else.unwrap.quickfix"),
                 """
                     public void printName(String name) {
                       if (name == null) {
                           throw new IllegalArgumentException();
                       } else/**/ {
                           System.out.println(name);
                       }
                   }
                   """,
                 """
                     public void printName(String name) {
                       if (name == null) {
                           throw new IllegalArgumentException();
                       }
                       System.out.println(name);
                   }
                   """
    );
  }

  public void testReturnStatement() {
    doMemberTest(InspectionGadgetsBundle.message("redundant.else.unwrap.quickfix"),
                 """
                     public int printName(String name) {
                       if (name == null) {
                           return -1;
                       } else/**/ {
                           System.out.println(name);
                       }
                       return 0;
                     }
                   """,
                 """
                     public int printName(String name) {
                       if (name == null) {
                           return -1;
                       }
                       System.out.println(name);
                       return 0;
                     }
                   """
    );
  }

  public void testBreakStatement() {
    doMemberTest(InspectionGadgetsBundle.message("redundant.else.unwrap.quickfix"),
                 """
                     public void printName(String[] texts) {
                       for (String text : texts) {
                         if ("illegal".equals(text)) {
                           break;
                         } else/**/ {
                           System.out.println(text);
                         }
                       }
                     }
                   """,
                 """
                     public void printName(String[] texts) {
                       for (String text : texts) {
                         if ("illegal".equals(text)) {
                           break;
                         }
                           System.out.println(text);
                       }
                     }
                   """
    );
  }

  public void testContinueStatement() {
    doMemberTest(InspectionGadgetsBundle.message("redundant.else.unwrap.quickfix"),
                 """
                     public void printName(String[] texts) {
                       for (String text : texts) {
                         if ("illegal".equals(text)) {
                           continue;
                         } else/**/ {
                           System.out.println(text);
                         }
                       }
                     }
                   """,
                 """
                     public void printName(String[] texts) {
                       for (String text : texts) {
                         if ("illegal".equals(text)) {
                           continue;
                         }
                           System.out.println(text);
                       }
                     }
                   """
    );
  }

  public void testDoNotFixElseWithoutJump() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("redundant.else.unwrap.quickfix"),
                               """
                                 class X {
                                   public void printName(String name) {
                                     if (name == null) {
                                         System.out.println("illegal");
                                     } else/**/ {
                                         System.out.println(name);
                                     }
                                   }
                                 }
                                 """);
  }
}
