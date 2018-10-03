// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class SwitchStatementWithTooManyBranchesInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doMemberTest("    public void foo(int x) {\n" +
                 "         /*'switch' has too many branches (11)*/switch/**/ (x) {\n" +
                 "            case 1:\n" +
                 "                break;\n" +
                 "            case 2:\n" +
                 "                break;\n" +
                 "            case 3:\n" +
                 "                break;\n" +
                 "            case 4:\n" +
                 "                break;\n" +
                 "            case 5:\n" +
                 "                break;\n" +
                 "            case 6:\n" +
                 "                break;\n" +
                 "            case 7:\n" +
                 "                break;\n" +
                 "            case 8:\n" +
                 "                break;\n" +
                 "            case 9:\n" +
                 "                break;\n" +
                 "            case 10:\n" +
                 "                break;\n" +
                 "            case 11:\n" +
                 "                break;\n" +
                 "            default:\n" +
                 "                break;\n" +
                 "        }\n" +
                 "    }");
  }

  public void testNoWarn() {
    doMemberTest("    public void foo(int x) {\n" +
                 "         switch (x) {\n" +
                 "            case 1:\n" +
                 "                break;\n" +
                 "            case 2:\n" +
                 "                break;\n" +
                 "            case 3:\n" +
                 "                break;\n" +
                 "            case 4:\n" +
                 "                break;\n" +
                 "            case 5:\n" +
                 "                break;\n" +
                 "            case 6:\n" +
                 "                break;\n" +
                 "            case 7:\n" +
                 "                break;\n" +
                 "            case 8:\n" +
                 "                break;\n" +
                 "            case 9:\n" +
                 "                break;\n" +
                 "            case 10:\n" +
                 "                break;\n" +
                 "            default:\n" +
                 "                break;\n" +
                 "        }\n" +
                 "    }");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new SwitchStatementWithTooManyBranchesInspection();
  }
}