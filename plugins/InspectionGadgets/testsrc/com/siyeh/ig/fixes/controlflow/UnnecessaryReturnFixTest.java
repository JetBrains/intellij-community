// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.UnnecessaryReturnInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class UnnecessaryReturnFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryReturnInspection();
  }

  public void testRemoveReturn() {
    doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "return"),
                 """
                     public void printName(String name) {
                       System.out.println(name);
                       return/**/;
                   }
                   """,
                 """
                     public void printName(String name) {
                       System.out.println(name);
                   }
                   """
    );
  }

  public void testRemoveReturnIntoIf() {
    doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "return"),
                 """
                     public void printName(String name) {
                       if (!"foo".equals(name)) {
                         System.out.println(name);
                         return/**/;
                       }
                     }
                   """,
                 """
                     public void printName(String name) {
                       if (!"foo".equals(name)) {
                         System.out.println(name);
                       }
                     }
                   """
    );
  }

  public void testRemoveReturnInConstructor() {
    doTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "return"),

           """
             class X {
               public X(String name) {
                 if (!"foo".equals(name)) {
                   System.out.println(name);
                   return/**/;
                 }
               }
             }
             """,

           """
             class X {
               public X(String name) {
                 if (!"foo".equals(name)) {
                   System.out.println(name);
                 }
               }
             }
             """
    );
  }

  public void testDoNotFixReturnWithValue() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "return"),
                               """
                                 class X {
                                   public int printName(String name) {
                                     System.out.println(name);
                                     return/**/ 0;
                                   }
                                 }
                                 """);
  }

  public void testDoNotFixFollowedReturn() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "return"),
                               """
                                 class X {
                                   public void printName(String name) {
                                     if (!"foo".equals(name)) {
                                       System.out.println(name);
                                       return/**/;
                                     }
                                     System.out.println("Bad code");
                                   }
                                 }
                                 """);
  }
}
