/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class SharedThreadLocalRandomInspectionTest extends LightInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    return new SharedThreadLocalRandomInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util.concurrent;\n" +
      "import java.util.Random;" +
      "public class ThreadLocalRandom extends Random {" +
      "  public static ThreadLocalRandom current() {" +
      "    return null;" +
      "  }" +
      "}"
    };
  }

  public void testNoWarn() {
    doStatementTest("System.out.println(java.util.concurrent.ThreadLocalRandom.current().nextInt());");
  }

  public void testArgument() {
    doStatementTest("System.out.println(java.util.concurrent.ThreadLocalRandom./*'ThreadLocalRandom' instance might be shared between threads*/current/**/());");
  }

  public void testNoWarn2() {
    doMemberTest("void m() {" +
                 "  java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();" +
                 "}");
  }

  public void testNestedClass() {
    doMemberTest("void m() {" +
                 "  java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom./*'ThreadLocalRandom' instance might be shared between threads*/current/**/();" +
                 "  class Z extends Thread {" +
                 "    public void run() {" +
                 "      System.out.println(r.nextInt(1));" +
                 "    }" +
                 "  }" +
                 "}");
  }

  public void testAssignmentToField() {
    doTest("import java.util.concurrent.ThreadLocalRandom;" +
           "class A {" +
           "  private ThreadLocalRandom r;" +
           "  void m() {" +
           "    r = ThreadLocalRandom./*'ThreadLocalRandom' instance might be shared between threads*/current/**/();" +
           "  }" +
           "}");
  }
}
