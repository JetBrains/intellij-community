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
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class LambdaParameterHidingMemberVariableInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    return new LambdaParameterHidingMemberVariableInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {"""
        package java.util.function;
        public interface Function<T, R> {
            R apply(T t);
        }"""};
  }

  public void testSimple() {
    doTest("""
             import java.util.function.Function;
             class X {
               private String s;

               void m() {
                 Function<String, String> f = (/*Lambda parameter 's' hides field in class 'X'*/s/**/) -> null;
               }
             }""");
  }

  public void testStatic() {
    doTest("""
             import java.util.function.Function;
             class X {
               private String s;
               static Function<String, String> m() {
                 return s -> null;
               }
             }""");
  }

  public void testStaticStatic() {
    doTest("""
             import java.util.function.Function;
             class X {
               private static String s;
               static Function<String, String> m() {
                 return /*Lambda parameter 's' hides field in class 'X'*/s/**/ -> null;
               }
             }""");
  }
}
