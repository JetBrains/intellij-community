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
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class UseOfConcreteClassInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UseOfConcreteClassInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {"class A {}"};
  }

  public void testForeach() {
    doMemberTest("void m(java.util.List<A> l) {" +
                 "  for (/*Local variable 'a' of concrete class 'A'*/A/**/ a : l) {}" +
                 "}");
  }

  public void testLocal() {
    doMemberTest("void m() {/*Local variable 'a' of concrete class 'A'*/A/**/ a;}");
  }

  public void testParameter() {
    doMemberTest("void m(/*Parameter 'a' of concrete class 'A'*/A/**/ a) {}");
  }
  
  public void testCatchParameter() {
    doMemberTest("void m() {try {toString();} catch (MyEx ex) {}} static class MyEx extends Error {}");
  }

  public void testStaticField() {
    doMemberTest("static /*Static field 'myA' of concrete class 'A'*/A/**/ myA;");
  }

  public void testInstanceField() {
    doMemberTest("/*Instance field 'myA' of concrete class 'A'*/A/**/ myA;");
  }

  public void testMethodReturn() {
    doMemberTest("/*Method returns a concrete class 'A'*/A/**/ getA() {return null;}");
  }

  public void testInstanceofInterfaces() {
    doTest();
  }

  public void testCastToConcreteClass() {
    doTest();
  }
}
