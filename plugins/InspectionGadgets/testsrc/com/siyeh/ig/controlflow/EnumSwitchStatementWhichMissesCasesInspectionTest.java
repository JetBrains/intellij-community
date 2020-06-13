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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("EnumSwitchStatementWhichMissesCases")
public class EnumSwitchStatementWhichMissesCasesInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("enum E { A, B, C }" +
           "class X {" +
           "  void m(E e) {" +
           "    /*'switch' statement on enum type 'E' misses case 'C'*/switch/**/ (e) {" +
           "      case A:" +
           "      case B:" +
           "    }" +
           "  }" +
           "}");
  }

  public void testTwoMissing() {
    doTest("enum E { A, B, C, D }" +
           "class X {" +
           "  void m(E e) {" +
           "    /*'switch' statement on enum type 'E' misses cases: 'C', and 'D'*/switch/**/ (e) {" +
           "      case A:" +
           "      case B:" +
           "    }" +
           "  }" +
           "}");
  }

  public void testManyMissing() {
    doTest("enum E { FIRST, SECOND, THIRD, FOURTH, FIFTH, SIXTH, SEVENTH, EIGHTH, NINTH }" +
           "class X {" +
           "  void m(E e) {" +
           "    /*'switch' statement on enum type 'E' misses cases: 'FIRST', 'SECOND', 'THIRD', 'FOURTH', 'FIFTH', ...*/switch/**/ (e) {" +
           "    }" +
           "  }" +
           "}");
  }

  public void testFullyCovered() {
    doTest("enum E { A, B, C }" +
           "class X {" +
           "  void m(E e) {" +
           "    switch(e) {" +
           "      case A:" +
           "      case B:" +
           "      case C:" +
           "    }" +
           "  }" +
           "}");
  }

  public void testUnresolved() {
    doTest("enum E { A, B, C }" +
           "class X {" +
           "  void m(E e) {" +
           "    switch(e) {" +
           "      case <error descr=\"Cannot resolve symbol 'D'\">D</error>:" +
           "    }" +
           "  }" +
           "}");
  }

  public void testSyntaxErrorInLabel() {
    doTest("enum E { A, B, C }" +
           "class X {" +
           "  void m(E e) {" +
           "    switch(e) {" +
           "      case <error descr=\"Constant expression required\">(A)</error>:" +
           "    }" +
           "  }" +
           "}");
  }

  public void testDfaFullyCovered() {
    doTest("enum E {A, B, C}\n" +
           "\n" +
           "class X {\n" +
           "  void m(E e) {\n" +
           "    if(e == E.C) return;\n" +
           "    switch ((e)) {\n" +
           "      case A:\n" +
           "      case B:\n" +
           "    }\n" +
           "  }\n" +
           "}");
  }

  public void testDfaNotCovered() {
    doTest("enum E {A, B, C}\n" +
           "\n" +
           "class X {\n" +
           "  void m(E e) {\n" +
           "    if(e == E.C || e == E.B) return;\n" +
           "    /*'switch' statement on enum type 'E' misses case 'A'*/switch/**/ (e) {\n" +
           "    }\n" +
           "  }\n" +
           "}");
  }

  public void testDfaPossibleValues() {
    doTest("enum E {A, B, C}\n" +
           "\n" +
           "class X {\n" +
           "  void m(E e) {\n" +
           "    if(e == E.A || e == E.B) {\n" +
           "      switch (e) {\n" +
           "        case A:\n" +
           "        case B:\n" +
           "      }\n" +
           "    }\n" +
           "  }\n" +
           "}");
  }

  public void testDfaPossibleValuesNotCovered() {
    doTest("enum E {A, B, C}\n" +
           "\n" +
           "class X {\n" +
           "  void m(E e) {\n" +
           "    if(e == E.A || e == E.B) {\n" +
           "      /*'switch' statement on enum type 'E' misses case 'B'*/switch/**/ (e) {\n" +
           "        case A:\n" +
           "      }\n" +
           "    }\n" +
           "  }\n" +
           "}");
  }

  public void testJava14() {
    doTest("enum E {A, B, C}\n" +
           "\n" +
           "class X {\n" +
           "  void m(E e) {\n" +
           "    switch(e) {\n" +
           "      case A -> {}\n" +
           "      case B -> {}\n" +
           "      case C -> {}\n" +
           "    }\n" +
           "    /*'switch' statement on enum type 'E' misses case 'C'*/switch/**/(e) {\n" +
           "      case A -> {}\n" +
           "      case B -> {}\n" +
           "    }\n" +
           "    /*'switch' statement on enum type 'E' misses case 'C'*/switch/**/(e) {\n" +
           "      case A, B -> {}\n" +
           "    }\n" +
           "    /*'switch' statement on enum type 'E' misses case 'C'*/switch/**/(e) {\n" +
           "      case A, B:break;\n" +
           "    }\n" +
           "    \n" +
           "  }\n" +
           "}");
  }


  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new EnumSwitchStatementWhichMissesCasesInspection();
  }
}