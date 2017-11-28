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
package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

public class JUnit5PlatformInspectionTest extends LightInspectionTestCase {

  public void testNoMethods() {
    doTest("import org.junit.Test;\n" +
           "import org.junit.platform.runner.JUnitPlatform;\n" +
           "import org.junit.runner.RunWith;\n" +
           "@RunWith(JUnitPlatform.class)\n" +
           "public class /*Class NoMethodsTest annotated @RunWith(JUnitPlatform.class) lacks test methods*/NoMethodsTest/**/ {}", "NoMethodsTest.java");
  }

  public void testNoPublicMethods() {
    doTest("import org.junit.Test;\n" +
           "import org.junit.platform.runner.JUnitPlatform;\n" +
           "import org.junit.runner.RunWith;\n" +
           "@RunWith(JUnitPlatform.class)\n" +
           "public class /*Class NoPublicMethodsTest annotated @RunWith(JUnitPlatform.class) lacks test methods*/NoPublicMethodsTest/**/ {\n" +
           "    @Test\n" +
           "    void name() throws Exception {\n" +
           "        System.out.println(\"Hello world\");\n" +
           "    }\n" +
           "}", "NoPublicMethodsTest.java");
  }

  public void testNoNoParamMethods() {
    doTest("import org.junit.Test;\n" +
           "import org.junit.platform.runner.JUnitPlatform;\n" +
           "import org.junit.runner.RunWith;\n" +
           "@RunWith(JUnitPlatform.class)\n" +
           "public class /*Class NoNoParamMethodsTest annotated @RunWith(JUnitPlatform.class) lacks test methods*/NoNoParamMethodsTest/**/ {\n" +
           "    @Test\n" +
           "    public void name(int i) throws Exception {\n" +
           "        System.out.println(\"Hello world\");\n" +
           "    }\n" +
           "}", "NoNoParamMethodsTest.java");
  }

  public void testWithRunnableMethods() {
    doTest("import org.junit.Test;\n" +
           "import org.junit.platform.runner.JUnitPlatform;\n" +
           "import org.junit.runner.RunWith;\n" +
           "@RunWith(JUnitPlatform.class)\n" +
           "public class WithRunnableMethodsTest {\n" +
           "    @Test\n" +
           "    public void name() throws Exception {\n" +
           "        System.out.println(\"Hello world\");\n" +
           "    }\n" +
           "}", "WithRunnableMethodsTest.java");
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package org.junit; public @interface Test{}",
      "package org.junit.platform.runner; public class JUnitPlatform {}",
      "package org.junit.runner; public @interface RunWith{Class value();}"
    };
  }


  @Override
  protected InspectionProfileEntry getInspection() {
    return new JUnit5PlatformInspection();
  }
}
