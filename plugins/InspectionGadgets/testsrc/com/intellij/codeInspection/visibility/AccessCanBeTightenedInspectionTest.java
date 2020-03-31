// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.visibility;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.intellij.lang.annotations.Language;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("WeakerAccess")
public class AccessCanBeTightenedInspectionTest extends LightJavaInspectionTestCase {
  private VisibilityInspection myVisibilityInspection;

  @Override
  protected LocalInspectionTool getInspection() {
    return myVisibilityInspection.getSharedLocalInspectionTool();
  }

  @Override
  protected void setUp() throws Exception {
    myVisibilityInspection = createTool();
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    myVisibilityInspection = null;
    super.tearDown();
  }

  private static VisibilityInspection createTool() {
    VisibilityInspection inspection = new VisibilityInspection();
    inspection.SUGGEST_PRIVATE_FOR_INNERS = true;
    inspection.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    inspection.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    return inspection;
  }

  @SuppressWarnings("FieldMayBeStatic")
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

  public void testUseOfPackagePrivateInAnnotation() {
    doTest("import java.util.*;\n" +
           "@interface Ann{ String value(); }\n" +
           "@Ann(value = C.VAL\n)" +
           "class C {\n" +
           "    static final String VAL = \"xx\";\n" +
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

  public void testSameFileInheritance() {
    doTest("class C {\n" +
           "  private static class Err {\n" +
           "    /*Access can be package-private*/public/**/ boolean notVisible() { return true; }\n" +
           "  }\n"+
           "  boolean f = new Err(){}.notVisible();\n" + //call on anonymous class!
           "}");
  }

  public void testAccessFromSubclass() {
    myFixture.allowTreeAccessForAllFiles();
    addJavaFile("x/Sub.java",
                "package x; " +
                "import y.C; " +
                "class Sub extends C {\n" +
                "  boolean f = new Err().isTVisible();\n" +
                "}");
    addJavaFile("y/C.java",
    "package y; public class C {\n" +
    "  public static class Err {\n" +
    "    public boolean isTVisible() { return true; }\n" +
    "  }\n" +
    "}");
    myFixture.configureByFiles("y/C.java","x/Sub.java");
    myFixture.checkHighlighting();
  }

  public void testQualifiedAccessFromSubclass() {
    myFixture.allowTreeAccessForAllFiles();
    addJavaFile("x/Sub.java", "package x; " +
                            "import y.C; " +
                            "class Sub extends C {\n" +
                            "  void bazz(C c) {\n" +
                            "    int a = c.foo; c.bar();" +
                            "  }\n" +
                            "}\n");
    addJavaFile("y/C.java", "package y; public class C {\n" +
                          "  public int foo = 0;\n" +
                          "  public void bar() {}\n" +
                          "}");
    myFixture.configureByFiles("y/C.java","x/Sub.java");
    myFixture.checkHighlighting();
  }

  public void testQualifiedAccessFromSubclassSamePackage() {
    myFixture.allowTreeAccessForAllFiles();
    addJavaFile("x/Sub.java", "package x; " +
                            "import y.C; " +
                            "class Sub extends C {}");
    addJavaFile("y/C.java", "package y; public class C {\n" +
                          "  public int foo = 0;\n" +
                          "  public void bar() {}\n" +
                          "}");
    addJavaFile("y/U.java", "package y; import x.Sub;\n" +
                           "public class U {{\n" +
                           "  Sub s = new Sub();\n" +
                           "  s.bar(); int a = s.foo;\n" +
                           " }}");
    myFixture.configureByFiles("y/C.java","x/Sub.java");
    myFixture.checkHighlighting();
  }

  public void testDoNotSuggestPrivateInAnonymousClassIfPrivatesForInnersIsOff() {
    myVisibilityInspection.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myVisibilityInspection.SUGGEST_PRIVATE_FOR_INNERS = false;

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

  public void testDoNotSuggestPrivateForInnerStaticSubclass() {
    doTest("class A {\n" +
           "    <warning descr=\"Access can be package-private\">protected</warning> String myElement;\n" +
           "    static class B extends A {\n" +
           "        @Override\n" +
           "        public String toString() {\n" +
           "            return myElement;\n" +
           "        }\n" +
           "    }\n" +
           "    <warning descr=\"Access can be private\">protected</warning> String myElement1;\n" +
           "    class B1 {\n" +
           "        @Override\n" +
           "        public String toString() {\n" +
           "            return myElement1;\n" +
           "        }\n" +
           "    }\n" +
           "}");
  }

  public void testStupidTwoPublicClassesInTheSamePackage() {
    myFixture.allowTreeAccessForAllFiles();
    addJavaFile("x/Sub.java", "package x; " +
                            "public class Sub {\n" +
                            "  Object o = new C();\n" +
                            "}\n");
    addJavaFile("x/C.java", "package x; \n" +
                          "<warning descr=\"Access can be package-private\">public</warning> class C {\n" +
                          "}");
    myFixture.configureByFiles("x/C.java", "x/Sub.java");
    myFixture.checkHighlighting();
  }

  public void testInterfaceIsImplementedByLambda() {
    myFixture.allowTreeAccessForAllFiles();
    addJavaFile("x/MyInterface.java", "package x;\n" +
                                    "public interface MyInterface {\n" +
                                    "  void doStuff();\n" +
                                    "}\n");
    addJavaFile("x/MyConsumer.java", "package x;\n" +
                                   "public class MyConsumer {\n" +
                                   "    public void doIt(MyInterface i) {\n" +
                                   "        i.doStuff();\n" +
                                   "    }\n" +
                                   "}");
    addJavaFile("y/Test.java", "package y;\n" +
                             "\n" +
                             "import x.MyConsumer;\n" +
                             "\n" +
                             "public class Test {\n" +
                             "    void ddd(MyConsumer consumer) {\n" +
                             "        consumer.doIt(() -> {});\n" +
                             "    }\n" +
                             "}");
    myFixture.configureByFiles("x/MyInterface.java", "y/Test.java", "x/MyConsumer.java");
    myFixture.checkHighlighting();
  }

  public void testInnerClassIsUnusedButItsMethodsAre() {
    myFixture.allowTreeAccessForAllFiles();
    addJavaFile("x/Outer.java", "package x;\n" +
                              "class Outer {\n" +
                              "    static Inner makeInner() {\n" +
                              "        return new Inner();\n" +
                              "    }\n" +
                              "\n" +
                              "    static class Inner {\n" +
                              "        void frob() {}\n" +
                              "    }\n" +
                              "}\n");
    addJavaFile("x/Consumer.java", "package x;\n" +
                                 "public class Consumer {\n" +
                                 "    public void doIt() {\n" +
                                 "        Outer.makeInner().frob();\n" +
                                 "    }\n" +
                                 "}");
    myFixture.configureByFiles("x/Outer.java", "x/Consumer.java");
    myFixture.checkHighlighting();
  }

  public void testNestedEnumWithReferenceByName() {
    myFixture.allowTreeAccessForAllFiles();
    addJavaFile("x/Outer.java", "package x;\n" +
                                "public class Outer {\n" +
                                "    enum E {A}\n" +
                                "    static final E abc = E.A;\n" +
                                "\n" +
                                "    public static void main(String[] args) {\n" +
                                "        System.out.println(abc.ordinal());\n" +
                                "    }\n" +
                                "}\n");
    addJavaFile("x/Consumer.java", "package x;\n" +
                                 "public class Consumer {\n" +
                                 "    public String foo() {\n" +
                                 "       return Outer.abc.name();\n" +
                                 "    }\n" +
                                 "}");
    myFixture.configureByFiles("x/Outer.java", "x/Consumer.java");
    myFixture.checkHighlighting();
  }

  public void testSuggestPackagePrivateForTopLevelClassSetting() {
    myFixture.allowTreeAccessForAllFiles();
    myVisibilityInspection.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    addJavaFile("x/Outer.java", "package x;\n" +
                              "public class Outer {\n" +
                              "\n" +
                              "}\n");
    addJavaFile("x/Consumer.java", "package x;\n" +
                                 "public class Consumer {\n" +
                                 "    public void doIt() {\n" +
                                 "        System.out.println(Outer.class.hashCode());\n" +
                                 "    }\n" +
                                 "}");
    myFixture.configureByFiles("x/Outer.java", "x/Consumer.java");
    myFixture.checkHighlighting();
  }

  public void testSuggestPackagePrivateForEntryPoint() {
    addJavaFile("x/MyTest.java", "package x;\n" +
                               "public class MyTest {\n" +
                               "    <warning descr=\"Access can be protected\">public</warning> void foo() {}\n" +
                               "}");
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), EntryPointsManagerBase.DEAD_CODE_EP_NAME, new EntryPointWithVisibilityLevel() {
      @Override
      public void readExternal(Element element) throws InvalidDataException {}

      @Override
      public void writeExternal(Element element) throws WriteExternalException {}

      @NotNull
      @Override
      public String getDisplayName() {
        return "accepted visibility";
      }

      @Override
      public boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement) {
        return isEntryPoint(psiElement);
      }

      @Override
      public boolean isEntryPoint(@NotNull PsiElement psiElement) {
        return psiElement instanceof PsiMethod && "foo".equals(((PsiMethod)psiElement).getName()) || psiElement instanceof PsiClass;
      }

      @Override
      public int getMinVisibilityLevel(PsiMember member) {
        return member instanceof PsiMethod && isEntryPoint(member) ? PsiUtil.ACCESS_LEVEL_PROTECTED : ACCESS_LEVEL_INVALID;
      }

      @Override
      public boolean isSelected() {
        return true;
      }

      @Override
      public void setSelected(boolean selected) {}

      @Override
      public String getTitle() {
        return getDisplayName();
      }

      @Override
      public String getId() {
        return getDisplayName();
      }
    }, getTestRootDisposable());
    myFixture.enableInspections(myVisibilityInspection.getSharedLocalInspectionTool());
    myFixture.configureByFiles("x/MyTest.java");
    myFixture.checkHighlighting();
  }

  public void testMinimalVisibilityForNonEntryPOint() {
    addJavaFile("x/MyTest.java", "package x;\n" +
                               "public class MyTest {\n" +
                               "    <warning descr=\"Access can be protected\">public</warning> void foo() {}\n" +
                               "    {foo();}\n" +
                               "}");
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), EntryPointsManagerBase.DEAD_CODE_EP_NAME, new EntryPointWithVisibilityLevel() {
      @Override
      public void readExternal(Element element) throws InvalidDataException {}

      @Override
      public void writeExternal(Element element) throws WriteExternalException {}

      @NotNull
      @Override
      public String getDisplayName() {
        return "accepted visibility";
      }

      @Override
      public boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement) {
        return false;
      }

      @Override
      public boolean isEntryPoint(@NotNull PsiElement psiElement) {
        return false;
      }

      @Override
      public int getMinVisibilityLevel(PsiMember member) {
        return member instanceof PsiMethod && "foo".equals(((PsiMethod)member).getName()) ? PsiUtil.ACCESS_LEVEL_PROTECTED : ACCESS_LEVEL_INVALID;
      }

      @Override
      public boolean isSelected() {
        return true;
      }

      @Override
      public void setSelected(boolean selected) {}

      @Override
      public String getTitle() {
        return getDisplayName();
      }

      @Override
      public String getId() {
        return getDisplayName();
      }
    }, getTestRootDisposable());
    myFixture.enableInspections(myVisibilityInspection.getSharedLocalInspectionTool());
    myFixture.configureByFiles("x/MyTest.java");
    myFixture.checkHighlighting();
  }

  public void testSuggestPackagePrivateForImplicitWrittenFields() {
    addJavaFile("x/MyTest.java", "package x;\n" +
                               "public class MyTest {\n" +
                               "    String foo;\n" +
                               "  {System.out.println(foo);}" +
                               "}");
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), ImplicitUsageProvider.EP_NAME, new ImplicitUsageProvider() {
      @Override
      public boolean isImplicitUsage(@NotNull PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitRead(@NotNull PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitWrite(@NotNull PsiElement element) {
        return element instanceof PsiField && "foo".equals(((PsiField)element).getName());
      }
    }, getTestRootDisposable());
    myFixture.configureByFiles("x/MyTest.java");
    myFixture.checkHighlighting();
  }

  @SuppressWarnings("FieldMayBeStatic")
  public void testSuggestForConstants() {
    myVisibilityInspection.SUGGEST_FOR_CONSTANTS = true;
    doTest("class SuggestForConstants {\n" +
           "    <warning descr=\"Access can be private\">public</warning> static final String MY_CONSTANT = \"a\";\n" +
           "    private final String myField = MY_CONSTANT;" +
           "}");
  }

  @SuppressWarnings("FieldMayBeStatic")
  public void testDoNotSuggestForConstants() {
    myVisibilityInspection.SUGGEST_FOR_CONSTANTS = false;
    doTest("class DoNotSuggestForConstants {\n" +
           "    public static final String MY_CONSTANT = \"a\";\n" +
           "    private final String myField = MY_CONSTANT;" +
           "}");
  }

  public void testSuggestPackagePrivateForMembersOfEvenPackagePrivateClass() {
    myFixture.allowTreeAccessForAllFiles();
    myVisibilityInspection.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    addJavaFile("x/Outer.java", "package x;\n" +
                              "public class Outer {\n" +
                              "  <warning descr=\"Access can be package-private\">public</warning> void foo() {}\n" +
                              "  public void fromOtherPackage() {}\n" +
                              "}\n");
    addJavaFile("x/Consumer.java",
               "package x;\n" +
               "public class Consumer {\n" +
               "    public void doIt(Outer o) {\n" +
               "        o.foo();\n" +
               "        o.fromOtherPackage();\n" +
               "    }\n" +
               "}");
    addJavaFile("y/ConsumerOtherPackage.java",
               "package y;\n" +
                    "public class ConsumerOtherPackage {\n" +
                    "    public void doIt(x.Outer o) {\n" +
                    "        o.fromOtherPackage();\n" +
                    "    }\n" +
                    "}");
    myFixture.configureByFiles("x/Outer.java", "x/Consumer.java", "y/ConsumerOtherPackage.java");
    myFixture.checkHighlighting();
  }

  private void addJavaFile(String relativePath, @Language("JAVA") String text) {
    myFixture.addFileToProject(relativePath, text);
  }
}