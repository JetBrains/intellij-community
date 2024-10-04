// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.rename.inplace.GrVariableInplaceRenameHandler;
import org.jetbrains.plugins.groovy.util.BaseTest;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class RenameTest extends GroovyLatestTest implements BaseTest {
  public RenameTest() {
    super("groovy/refactoring/rename/");
  }

  @Test
  public void closureIt() { doTest(); }

  @Test
  public void toGetter() { doTest(); }

  @Test
  public void toProp() { doTest(); }

  @Test
  public void toSetter() { doTest(); }

  @Test
  public void scriptMethod() { doTest(); }

  @Test
  public void parameterIsNotAUsageOfGroovyParameter() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
      def foo(f) {
        // Parameter
        println 'Parameter' // also
        return <caret>f
      }
      """);
    String txt = "Just the Parameter word, which shouldn't be renamed";
    PsiFile txtFile = fixture.addFileToProject("a.txt", txt);

    PsiElement parameter = fixture.getFile().findReferenceAt(fixture.getEditor().getCaretModel().getOffset()).resolve();
    fixture.renameElement(parameter, "newName", true, true);
    fixture.checkResult("""
                               def foo(newName) {
                                 // Parameter
                                 println 'Parameter' // also
                                 return <caret>newName
                               }
                               """);
    Assert.assertEquals(txt, txtFile.getText());
  }

  @Test
  public void preserveUnknownImports() {
    JavaCodeInsightTestFixture fixture = getFixture();
    PsiClass someClass = fixture.addClass("public class SomeClass {}");
    fixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
      import foo.bar.Zoo
      SomeClass c = new SomeClass()
      Zoo zoo
      """);
    fixture.renameElement(someClass, "NewClass");
    fixture.checkResult("""
                               import foo.bar.Zoo
                               NewClass c = new NewClass()
                               Zoo zoo
                               """);
  }

  @Test
  public void renameGetter() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.addFileToProject("Foo.groovy", "class Foo { def getFoo(){return 2}}");
    PsiMethod[] methods = fixture.findClass("Foo").findMethodsByName("getFoo", false);
    fixture.configureByText("a.groovy", "print new Foo().foo");
    fixture.renameElement(methods[0], "get");
    fixture.checkResult("print new Foo().get()");
  }

  @Test
  public void renameSetter() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.addFileToProject("Foo.groovy", "class Foo { def setFoo(def foo){}}");
    PsiMethod[] methods = fixture.findClass("Foo").findMethodsByName("setFoo", false);
    fixture.configureByText("a.groovy", "print new Foo().foo = 2");
    fixture.renameElement(methods[0], "set");
    fixture.checkResult("print new Foo().set(2)");
  }

  @Test
  public void property() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText("a.groovy", """
      class Foo {
        def p<caret>rop
      
        def foo() {
          print prop
      
          print getProp()
      
          setProp(2)
        }
      }""");
    PsiElement field = fixture.getElementAtCaret();
    fixture.renameElement(field, "newName");
    fixture.checkResult("""
                               class Foo {
                                 def newName
                               
                                 def foo() {
                                   print newName
                               
                                   print getNewName()
                               
                                   setNewName(2)
                                 }
                               }""");
  }

  @Test
  public void propertyWithLocalCollision() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText("a.groovy", """
      class Foo {
        def p<caret>rop
      
        def foo() {
          def newName = a;
          print prop
      
          print getProp()
      
          setProp(2)
      
          print newName
        }
      }""");
    PsiElement field = fixture.getElementAtCaret();
    fixture.renameElement(field, "newName");
    fixture.checkResult("""
                               class Foo {
                                 def newName
                               
                                 def foo() {
                                   def newName = a;
                                   print this.newName
                               
                                   print getNewName()
                               
                                   setNewName(2)
                               
                                   print newName
                                 }
                               }""");
  }

  @Test
  public void propertyWithFieldCollision() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText("a.groovy", """
      class A {
        String na<caret>me;
      
        class X {
      
          String ndame;
          void foo() {
              print name
      
              print getName()
      
              setName("a")
          }
        }
      }""");
    PsiElement field = fixture.getElementAtCaret();
    fixture.renameElement(field, "ndame");
    fixture.checkResult("""
                               class A {
                                 String ndame;
                               
                                 class X {
                               
                                   String ndame;
                                   void foo() {
                                       print A.this.ndame
                               
                                       print A.this.getNdame()
                               
                                       A.this.setNdame("a")
                                   }
                                 }
                               }""");
  }

  @Test
  public void renameFieldWithNonstandardName() {
    JavaCodeInsightTestFixture fixture = getFixture();
    PsiFile file = fixture.configureByText("a.groovy", """
      class SomeBean {
        String xXx<caret> = "field"
        public String getxXx() {
          return "method"
        }
        public static void main(String[] args) {
          println(new SomeBean().xXx)
          println(new SomeBean().getxXx())
        }
      }
      """);
    final PsiClass clazz = fixture.findClass("SomeBean");
    fixture.renameElement(new PropertyForRename(List.of(clazz.findFieldByName("xXx", false), clazz.findMethodsByName("getxXx", false)[0]),
                                                "xXx", PsiManager.getInstance(getProject())), "xXx777");
    Assert.assertEquals("""
                          class SomeBean {
                            String xXx777 = "field"
                            public String getxXx777() {
                              return "method"
                            }
                            public static void main(String[] args) {
                              println(new SomeBean().xXx777)
                              println(new SomeBean().getxXx777())
                            }
                          }
                          """, file.getText());
  }

  @Test
  public void renameClassWithConstructorWithOptionalParams() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText("a.groovy", """
      class Test {
        def Test(def abc = null){}
      }
      
      print new Test()
      print new Test(1)
      """);
    fixture.renameElement(fixture.findClass("Test"), "Foo");
    fixture.checkResult("""
                               class Foo {
                                 def Foo(def abc = null){}
                               }
                               
                               print new Foo()
                               print new Foo(1)
                               """);
  }

  public void doTest() {
    final String testFile = getTestName().replace("$", "/") + ".test";
    final List<String> list = TestUtils.readInput(TestUtils.getAbsoluteTestDataPath() + "groovy/refactoring/rename/" + testFile);

    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, list.get(0));

    PsiReference ref = fixture.getFile().findReferenceAt(fixture.getEditor().getCaretModel().getOffset());
    final PsiElement resolved = ref == null ? null : ref.resolve();
    if (resolved instanceof PsiMethod method && !(resolved instanceof GrAccessorMethod)) {
      String name = method.getName();
      String newName = createNewNameForMethod(name);
      fixture.renameElementAtCaret(newName);
    }
    else if (resolved instanceof GrAccessorMethod method) {
      GrField field = method.getProperty();
      RenameProcessor processor = new RenameProcessor(fixture.getProject(), field, "newName", true, true);
      processor.addElement(resolved, createNewNameForMethod(method.getName()));
      processor.run();
    }
    else {
      fixture.renameElementAtCaret("newName");
    }
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    fixture.checkResult(list.get(1));
  }

  private static String createNewNameForMethod(final String name) {
    String newName = "newName";
    if (name.startsWith("get")) {
      newName = "get" + StringUtil.capitalize(newName);
    }
    else if (name.startsWith("is")) {
      newName = "is" + StringUtil.capitalize(newName);
    }
    else if (name.startsWith("set")) {
      newName = "set" + StringUtil.capitalize(newName);
    }
    return newName;
  }

  @Ignore("The fix requires major changes in property resolution and platform renamer")
  @Test
  public void recursivePathRename() {
    JavaCodeInsightTestFixture fixture = getFixture();
    PsiFile file = fixture.configureByText("SomeBean.groovy", """
      class SomeBean {
      
        SomeBean someBean<caret>
      
        static {
          new SomeBean().someBean.someBean.someBean.someBean.toString()
        }
      }
      """);
    fixture.renameElementAtCaret("b");
    Assert.assertEquals("""
                          class SomeBean {
                          
                            SomeBean b
                          
                            static {
                              new SomeBean().b.b.b.b.toString()
                            }
                          }
                          """, file.getText());
  }

  @Test
  public void dontAutoRenameDynamicallyTypeUsage() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText("a.groovy", """
      class Goo {
        def pp<caret>roject() {}
      }
      
      new Goo().pproject()
      
      def foo(p) {
        p.pproject()
      }
      """);
    GrMethod method =
      PsiTreeUtil.findElementOfClassAtOffset(fixture.getFile(), fixture.getEditor().getCaretModel().getOffset(), GrMethod.class, false);
    UsageInfo[] usages = RenameUtil.findUsages(method, "project", false, false, Map.of(method, "project"));
    assert (usages[0].isNonCodeUsage ? 1 : 0) + (usages[1].isNonCodeUsage ? 1 : 0) == 1;
  }

  @Test
  public void renameAliasImportedProperty() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.addFileToProject("Foo.groovy", """
      class Foo {
      static def bar
      }""");
    fixture.configureByText("a.groovy", """
      import static Foo.ba<caret>r as foo
      
      print foo
      print getFoo()
      setFoo(2)
      foo = 4""");
    fixture.renameElement(fixture.findClass("Foo").getFields()[0], "newName");
    fixture.checkResult("""
                               import static Foo.newName as foo
                               
                               print foo
                               print getFoo()
                               setFoo(2)
                               foo = 4""");
  }

  @Test
  public void renameAliasImportedClass() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.addFileToProject("Foo.groovy", """
      class Foo {
      static def bar
      }""");
    fixture.configureByText("a.groovy", """
      import Foo as Bar
      Bar bar = new Bar()
      """);
    fixture.renameElement(fixture.findClass("Foo"), "F");
    fixture.checkResult("""
                               import F as Bar
                               Bar bar = new Bar()
                               """);
  }

  @Test
  public void renameAliasImportedMethod() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.addFileToProject("Foo.groovy", """
      class Foo {
      static def bar(){}
      }""");
    fixture.configureByText("a.groovy", """
      import static Foo.bar as foo
      foo()
      """);
    fixture.renameElement(fixture.findClass("Foo").findMethodsByName("bar", false)[0], "b");
    fixture.checkResult("""
                               import static Foo.b as foo
                               foo()
                               """);
  }

  @Test
  public void renameAliasImportedField() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.addFileToProject("Foo.groovy", """
      class Foo {
      public static bar
      }""");
    fixture.configureByText("a.groovy", """
      import static Foo.ba<caret>r as foo
      
      print foo
      foo = 4""");
    fixture.renameElement(fixture.findClass("Foo").getFields()[0], "newName");
    fixture.checkResult("""
                               import static Foo.newName as foo
                               
                               print foo
                               foo = 4""");
  }

  @Test
  public void inplaceRename() {
    doInplaceRenameTest();
  }

  @Test
  public void inplaceRenameWithGetter() {
    doInplaceRenameTest();
  }

  @Test
  public void inplaceRenameWithStaticField() {
    doInplaceRenameTest();
  }

  @Test
  public void inplaceRenameOfClosureImplicitParameter() {
    doInplaceRenameTest();
  }

  @Test
  public void renameClassWithLiteralUsages() {
    JavaCodeInsightTestFixture fixture = getFixture();
    PsiFile file = fixture.addFileToProject("aaa.groovy", """
            class Foo {
              Foo(int a) {}
            }
            def x = [2] as Foo
            def y  = ['super':2] as Foo
      """);
    fixture.configureFromExistingVirtualFile(file.getVirtualFile());
    fixture.renameElement(fixture.findClass("Foo"), "Bar");
    fixture.checkResult("""
                                     class Bar {
                                       Bar(int a) {}
                                     }
                                     def x = [2] as Bar
                                     def y  = ['super':2] as Bar
                               """);
  }

  @Test
  public void extensionOnClassRename() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText("Foo.gy", "class Foo {}");
    fixture.renameElement(fixture.findClass("Foo"), "Bar");
    assert "gy".equals(fixture.getFile().getVirtualFile().getExtension());
  }

  @Test
  public void renameJavaUsageFail() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.addFileToProject("Bar.java", """
      class Bar {
        void bar() {
          new Foo().foo();
        }
      }""");
    fixture.configureByText("Foo.groovy", """
      class Foo {
        def foo() {}
      }""");
    try {
      fixture.renameElement(fixture.findClass("Foo").getMethods()[0], "'newName'");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      Assert.assertEquals("<b><code>'newName'</code></b> is not a correct identifier to use in <b><code>new Foo().foo</code></b>",
                          e.getMessage());
      return;
    }
    Assert.fail();
  }

  @Test
  public void renameJavaPrivateField() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.addFileToProject("Foo.java", """
      public class Foo {
        private int field;
      }""");
    fixture.configureByText("Bar.groovy", """
      print new Foo(field: 2)
      """);
    fixture.renameElement(fixture.findClass("Foo").getFields()[0], "anotherOneName");
    fixture.checkResult("""
                               print new Foo(anotherOneName: 2)
                               """);
  }

  @Test
  public void renameProp() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText("Foo.groovy", """
      class Book {
          String title
      }
      
      class Test {
       def testBook(){
            def book = new Book()
      
            book.with {
                title = 'Test'
            }
        }
      }""");
    new PropertyRenameHandler().invoke(getProject(), new PsiElement[]{fixture.findClass("Book").getFields()[0]}, null);
    fixture.checkResult("""
                               class Book {
                                   String s
                               }
                               
                               class Test {
                                def testBook(){
                                     def book = new Book()
                               
                                     book.with {
                                         s = 'Test'
                                     }
                                 }
                               }""");
  }

  private void doInplaceRenameTest() {
    String prefix = "/" + StringUtil.capitalize(getTestName());
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByFile(prefix + ".groovy");
    CommandProcessor.getInstance().executeCommand(getProject(), () -> {
      CodeInsightTestUtil.doInlineRename(new GrVariableInplaceRenameHandler(), "foo", fixture);
    }, "Rename", null);
    fixture.checkResultByFile(prefix + "_after.groovy");
  }

  @Test
  public void renameJavaGetter() {
    getFixture().configureByText("J.java", """
      class J {
        int ge<caret>tFoo() {return 2;}
      }
      """);
    PsiFile groovyFile = getFixture().addFileToProject("g.groovy", """
      print new J().foo""");
    getFixture().renameElementAtCaret("getAbc");
    Assert.assertEquals("""
                          print new J().abc""", groovyFile.getText());
  }

  @Test
  public void methodWithSpacesRename() {
    JavaCodeInsightTestFixture fixture = getFixture();
    GroovyFile file = (GroovyFile)fixture.configureByText("_A.groovy", """
      class X {
        def foo(){}
      }
      
      new X().foo()
      """);
    GrMethod method = ((GrTypeDefinition)file.getClasses()[0]).getCodeMethods()[0];
    fixture.renameElement(method, "f oo");
    fixture.checkResult("""
                               class X {
                                 def 'f oo'(){}
                               }
                               
                               new X().'f oo'()
                               """);
  }

  @Test
  public void methodWithSpacesRenameInJava() {
    JavaCodeInsightTestFixture fixture = getFixture();
    GroovyFile file = (GroovyFile)fixture.addFileToProject("_A.groovy", """
      class X {
        def foo(){}
      }
      
      new X().foo()
      """);
    GrMethod method = ((GrTypeDefinition)file.getClasses()[0]).getCodeMethods()[0];
    fixture.configureByText("Java.java", """
      class Java {
        void ab() {
          new X().foo()
        }
      }""");
    try {
      fixture.renameElement(method, "f oo");
      assert false;
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException ignored) {
      assert true;
    }
  }

  @Test
  public void tupleConstructor() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText("a.groovy", """
          import groovy.transform.TupleConstructor
          
          @TupleConstructor
          class X<caret>x {}
          """);
    fixture.renameElementAtCaret("Yy");
    fixture.checkResult("""
                      import groovy.transform.TupleConstructor
                      
                      @TupleConstructor
                      class Y<caret>y {}
                      """);
  }

  @Test
  public void constructor() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText("a.groovy", """
          class Foo {
            def Fo<caret>o() {}
          }
          """);
    fixture.renameElementAtCaret("Bar");
    fixture.checkResult("""
                      class Bar {
                        def Ba<caret>r() {}
                      }
                      """);
  }

  @Test
  public void stringNameForMethod() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, "def fo<caret>o() {}");
    fixture.renameElementAtCaret("import");
    fixture.checkResult("def 'import'() {}");
  }

  @Test
  public void constructorAndSuper() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
          class B<caret>ase {
            def Base() {}
          }
          class Inheritor extends Base {
            def Inheritor() {
              super()
            }
          }
          """);
    fixture.renameElementAtCaret("Bassse");
    fixture.checkResult("""
                      class Bassse {
                        def Bassse() {}
                      }
                      class Inheritor extends Bassse {
                        def Inheritor() {
                          super()
                        }
                      }
                      """);
  }

  @Test
  public void overridenMethodWithOptionalParams() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
          class Base {
            void fo<caret>o(){}
          }
          
          class Inheritor extends Base {
            void foo(int x = 5) {}
          }
          
          new Base().foo()
          new Inheritor().foo()
          new Inheritor().foo(2)
          """);
    fixture.renameElementAtCaret("bar");
    fixture.checkResult("""
                      class Base {
                        void ba<caret>r(){}
                      }
                      
                      class Inheritor extends Base {
                        void bar(int x = 5) {}
                      }
                      
                      new Base().bar()
                      new Inheritor().bar()
                      new Inheritor().bar(2)
                      """);
  }

  @Test
  public void renameScriptFile() {
    JavaCodeInsightTestFixture fixture = getFixture();
    final PsiFile file = fixture.configureByText("Abc.groovy", "print new Abc()\n");
    fixture.renameElement(file, "Abcd.groovy");
    fixture.checkResult("print new Abcd()\n");
  }

  @Test
  public void traitField() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText("a.groovy", """
          trait T {
              public int f<caret>oo = 5
          
              def bar() {
                  print foo
              }
          }
          
          class X implements T {
             def bar() {
                print T__foo
             }
          }
          
          trait T2 extends T {
              def bar() {
                  print T__foo
              }
          }
          """);
    fixture.renameElementAtCaret("baz");
    fixture.checkResult("""
                      trait T {
                          public int baz = 5
                      
                          def bar() {
                              print baz
                          }
                      }
                      
                      class X implements T {
                         def bar() {
                            print T__baz
                         }
                      }
                      
                      trait T2 extends T {
                          def bar() {
                              print T__baz
                          }
                      }
                      """);
  }

  @Test
  public void renameReflectedMethodWithOverloads() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.configureByText("_.groovy", """
          class A {
            def fo<caret>o(a, b, c = 1) {}
            def foo(d = 2) {}
          }
          """);
    fixture.renameElementAtCaretUsingHandler("foo1");
    fixture.checkResult("""
                      class A {
                        def foo1(a, b, c = 1) {}
                        def foo1(d = 2) {}
                      }
                      """);
  }

  @Test
  public void importCollisionInJavaAfterClassRename() {
    JavaCodeInsightTestFixture fixture = getFixture();
    PsiFile usage = fixture.addFileToProject("Usage.java", """
      import java.util.*;
      
      class C implements List, p.MyList {}
      """);
    fixture.addFileToProject("p/intentionallyNonClassName.groovy", "package p; class MyList {}");
    fixture.renameElement(fixture.findClass("p.MyList"), "List");
    assert usage.getText().equals("""
                                    import java.util.*;
                                    
                                    class C implements List, p.List {}
                                    """);
  }
}
