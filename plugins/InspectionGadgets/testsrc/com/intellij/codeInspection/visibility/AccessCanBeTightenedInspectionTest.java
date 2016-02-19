/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.visibility;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.util.ReflectionUtil;
import com.siyeh.ig.LightInspectionTestCase;

public class AccessCanBeTightenedInspectionTest extends LightInspectionTestCase {
  public void testSimple() {
    doTest("import java.util.*;\n" +
           "class C {\n" +
           "    final int /*Access can be private*/fd/**/ = 0;\n" +
           "    /*Access can be private*/public/**/ int fd2;\n" +
           "    /*Access can be package-private*/public/**/ int forSubClass;\n" +
           "    @Override\n" +
           "    public int hashCode() {\n" +
           "      return fd + fd2;\n" + // use field
           "    }\n" +
           "" +
           " public void fff() {\n" +
           "   class Local {\n" +
           "        int /*Access can be private*/fd/**/;\n" +
           "        void f(){}\n" + // unused, ignore
           "        void /*Access can be private*/fd/**/(){}\n" +
           "        @Override\n" +
           "        public int hashCode() {\n" +
           "          fd();\n" +
           "          return fd;\n" +
           "        }\n" +
           "        class CantbePrivate {}\n" +
           "   }\n" +
           " }\n" +
           "}\n" +
           "class Over extends C {" +
           "  int r = forSubClass;" +
           "  @Override " +
           "  public void fff() {}" +
           "}");
  }
  public void testUseInAnnotation() {
    doTest("import java.util.*;\n" +
           "@interface Ann{ String value(); }\n" +
           "@Ann(value = C.VAL\n)" +
           "class C {\n" +
           "    /*Access can be package-private*/public/**/ static final String VAL = \"xx\";\n" +
           "}");
  }

  public void testSameFile() {
    doTest("class C {\n" +
           "  private static class Err {\n" +
           "    /*Access can be private*/public/**/ boolean isVisible() { return true; }\n" +
           "  }\n"+
           "  boolean f = new Err().isVisible();\n" +
           "}");
  }

  public void testAccessFromSubclass() {
    myFixture.allowTreeAccessForAllFiles();
    myFixture.addFileToProject("x/Sub.java",
      "package x; " +
      "import y.C; " +
      "class Sub extends C {\n" +
      "  boolean f = new Err().isTVisible();\n" +
      "}\n" +
      "");
    myFixture.addFileToProject("y/C.java",
      "package y; public class C {\n" +
      "  public static class Err {\n" +
      "    public boolean isTVisible() { return true; }\n" +
      "  }\n"+
      "}");
    myFixture.configureByFiles("y/C.java","x/Sub.java");
    myFixture.checkHighlighting();
  }

  public void testDoNotSuggestPrivateInAnonymousClassIfPrivatesForInnersIsOff() {
    InspectionProfileImpl profile = (InspectionProfileImpl)InspectionProjectProfileManager.getInstance(getProject()).getInspectionProfile();
    AccessCanBeTightenedInspection inspection = (AccessCanBeTightenedInspection)profile.getInspectionTool(VisibilityInspection.SHORT_NAME, getProject()).getTool();
    VisibilityInspection visibilityInspection =
      ReflectionUtil.getField(inspection.getClass(), inspection, VisibilityInspection.class, "myVisibilityInspection");
    visibilityInspection.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    visibilityInspection.SUGGEST_PRIVATE_FOR_INNERS = false;

    doTest("class C {\n" +
           " {\n" +
           "  new Runnable() {\n" +
           "    @Override\n" +
           "    public void run() { isDisposed = true; }\n"+
           "    boolean isVisible() { return true; }\n" +
           "    boolean isDisposed;\n" +
           "  }.run();\n" +
           " }\n"+
           "}");
  }

  public void testDoNotSuggestPrivateIfInExtendsOrImplements() {
    doTest("abstract class C implements Comparable<C.Inner> {\n" +
           "  static class Inner {\n" +
           "  }\n"+
           "}");
  }

  public void testDoNotSuggestPrivateForAbstractIDEA151875() {
    doTest("class C {\n" +
           "  abstract static class Inner {\n" +
           "    abstract void foo();\n"+
           "  }\n" +
           "  void f(Inner i) {\n" +
           "    i.foo();\n" +
           "  }\n"+
           "}");
  }

  @Override
  protected LocalInspectionTool getInspection() {
    VisibilityInspection inspection = new VisibilityInspection();
    inspection.SUGGEST_PRIVATE_FOR_INNERS = true;
    inspection.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    inspection.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    return inspection.getSharedLocalInspectionTool();
  }
}