/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class SuspiciousSystemArraycopyInspectionTest extends LightJavaInspectionTestCase {

  public void testEmptyDst() {
    doMemberTest("void m(int[] src) {" +
                 "  System.arraycopy(src, 0,/*!Expression expected*/ /*!*/, 0, src.length);" +
                 "}");
  }

  public void testEmptySrc() {
    doMemberTest("void m(int[] dst) {" +
                 "  System.arraycopy(/*!Expression expected*/,/*!*/ 0, dst, 0, 10);" +
                 "}");
  }


  public void testLengthAlwaysGreater() {
    doMemberTest("public int[] hardCase() {\n" +
                 "        int[] src = new int[] { 1, 2, 3 };\n" +
                 "        int[] dest = new int[] { 4, 5, 6, 7, 8, 9 };\n" +
                 "        System.arraycopy(src, 2, dest, 2, /*Length is always bigger than 'src.length - srcPos' {2}*/2/**/);\n" +
                 "        return dest;\n" +
                 "    }");
  }

  public void testLengthNotAlwaysGreater() {
    doMemberTest("public int[] hardCase(boolean outer) {\n" +
                 "        int[] src = new int[] { 1, 2, 3};\n" +
                 "        int[] dest = new int[] { 4, 5, 6, 7, 8, 9 };\n" +
                 "        int length;\n" +
                 "        if (outer) {\n" +
                 "            length = 3; // maybe this branch is never reached due to outer condition\n" +
                 "        } else {\n" +
                 "            length = 1;\n" +
                 "        }\n" +
                 "        System.arraycopy(src, 2, dest, 2, length);\n" +
                 "        return dest;\n" +
                 "    }");
  }

  public void testRangesNotIntersect() {
    doMemberTest("public void process() {\n" +
                 "        int[] src = new int[] { 1, 2, 3, 4 };\n" +
                 "        System.arraycopy(src, 0, src, 2, 2);\n" +
                 "    }");
  }

  public void testRangesIntersect() {
    doMemberTest("    public void rangesIntersects() {\n" +
                 "        int[] src = new int[] { 1, 2, 3, 4 };\n" +
                 "        System./*Copying to the same array with intersecting ranges*/arraycopy/**/(src, 0, src, 1, 2);\n" +
                 "    }");
  }

  public void testRangesIntersectSometimes() {
    doMemberTest("public void rangesIntersects(boolean outer) {\n" +
                 "        int[] src = new int[] { 1, 2, 3, 4, 5 };\n" +
                 "        int srcPos;\n" +
                 "        if (outer) {\n" +
                 "            srcPos = 0;\n" +
                 "        } else {\n" +
                 "            srcPos = 1; // maybe this branch never reached due to outer condition\n" +
                 "        }\n" +
                 "        System.arraycopy(src, srcPos, src, 2, 2);\n" +
                 "    }");
  }

  public void testRangeEndMayBeBiggerStart() {
    doMemberTest("public void hardCase(boolean outer) {\n" +
                 "        int[] src = new int[] { 1, 2, 3, 4, 5, 6, 7 };\n" +
                 "        int length = outer ? 1 : 3;\n" +
                 "        int srcPos = outer ? 0 : 3;\n" +
                 "        int destPos = outer ? 1 : 4;\n" +
                 "        System.arraycopy(src, srcPos, src, destPos, length);\n" +
                 "    }");
  }


  public void testCopyFull() {
    doMemberTest("    public static void copyFull(byte[] a2, byte[] a1) {\n" +
                 "        assert (a1.length == 4);\n" +
                 "        assert (a2.length == 8);\n" +
                 "        System.arraycopy(a1, 0, a2, 4, a1.length);\n" +
                 "    }");
  }

  public void test248060() {
    doMemberTest("public class ArrayCopyExample {\n" +
                 "    private double[] margins = new double[4];\n" +
                 "\n" +
                 "    public ArrayCopyExample() {}\n" +
                 "\n" +
                 "    public ArrayCopyExample(ArrayCopyExample original) {\n" +
                 "        System.arraycopy(original.margins, 0, margins, 0, 4);\n" +
                 "    }\n" +
                 "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new SuspiciousSystemArraycopyInspection();
  }
}