// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyUncheckedAssignmentOfMemberOfRawTypeInspection;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;

import java.util.*;

public class GroovyMapAttributeTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_1_7;
  }

  private void doTestCompletion(String fileText, boolean exists) {
    myFixture.configureByText("a.groovy", fileText);
    LookupElement[] res = myFixture.completeBasic();

    TestCase.assertNotNull(res);

    Set<String> variants = new HashSet<String>();

    for (LookupElement element : res) {
      variants.add(element.getLookupString());
    }


    if (exists) {
      TestCase.assertTrue(variants.contains("sss1"));
      TestCase.assertTrue(variants.contains("sss2"));
    }
    else {
      TestCase.assertFalse(variants.contains("sss1"));
      TestCase.assertFalse(variants.contains("sss2"));
    }
  }

  public void testEmptyConstructorCompletion() {
    doTestCompletion("""
                       
                       class Aaa {
                         String sss1
                         String sss2
                       
                         public Aaa() {}
                       
                         static {
                           new Aaa(<caret>)
                         }
                       }
                       """, true);
  }

  public void testNoDefaultConstructor() {
    doTestCompletion("""
                       
                       class Aaa {
                         String sss1
                         String sss2
                       
                         public Aaa(String sss) {}
                       
                         static {
                           new Aaa(<caret>)
                         }
                       }
                       """, false);
  }

  public void testDefaultConstructorAndNonDefault() {
    doTestCompletion("""
                       
                       class Aaa {
                         String sss1
                         String sss2
                       
                         public Aaa() {}
                       
                         public Aaa(String sss) {}
                       
                         static {
                           new Aaa(<caret>)
                         }
                       }
                       """, true);
  }

  public void testHasMapConstructor() {
    doTestCompletion("""
                       
                       class Aaa {
                         String sss1
                         String sss2
                       
                         public Aaa() {
                         }
                       
                         public Aaa(Map mmm) {}
                       
                         static {
                           new Aaa(<caret>)
                         }
                       }
                       """, true);
  }

  public void testHasHashMapConstructor() {
    doTestCompletion("""
                       
                       class Aaa {
                         String sss1
                         String sss2
                       
                         public Aaa() {
                         }
                       
                         public Aaa(java.util.HashMap mmm) {}
                       
                         static {
                           new Aaa(<caret>)
                         }
                       }
                       """, true);
  }

  public void testMapNotFirstConstructor() {
    doTestCompletion("""
                       
                       class Aaa {
                         String sss1
                         String sss2
                       
                         public Aaa() {}
                       
                         public Aaa(int x, Map mmm) {}
                       
                         static {
                           new Aaa(<caret>)
                         }
                       }
                       """, true);
  }

  public void testCallOtherConstructor() {
    doTestCompletion("""
                       
                       class Aaa {
                         String sss1
                         String sss2
                       
                         public Aaa() {}
                       
                         public Aaa(String s) {
                           this(sss<caret>: )
                         }
                       }
                       """, false);
  }

  public void testWithMap() {
    doTestCompletion("""
                       
                       class Aaa {
                         String sss1
                         String sss2
                       
                         public Aaa() {}
                       
                         public Aaa(Map map, String s) {
                       
                         }
                       
                         static {
                           new Aaa(<caret>)
                         }
                       }
                       """, true);
  }

  public void testWithMap2() {
    doTestCompletion("""
                       
                       class Aaa {
                         String sss1
                         String sss2
                       
                         public Aaa() {}
                       
                         public Aaa(Map map, String s = null) {
                       
                         }
                       
                         static {
                           new Aaa(<caret>)
                         }
                       }
                       """, true);
  }

  public void testAlreadyHasNonMapParameter() {
    doTestCompletion("""
                       
                       class Aaa {
                         String sss1
                         String sss2
                       
                         public Aaa() {}
                       
                         static {
                           new Aaa(1, <caret>)
                         }
                       }
                       """, false);
  }

  public void testAlreadyHasNonMapParameter1() {
    doTestCompletion("""
                       
                       class Aaa {
                         String sss1
                         String sss2
                       
                         public Aaa() {}
                         public Aaa(String s) {}
                       
                         static {
                           new Aaa(<caret>, "dff")
                         }
                       }
                       """, false);
  }

  public void testConstructorInJavaClass() {
    myFixture.addFileToProject("Ccc.java", """
      
      public class Ccc {
        private String sss1
        private String sss2
      
        private void setSss3(String s) {}
        private void setSss4(String s) {}
      
        public Aaa() {}
        public Aaa(String s) {}
      }
      """);

    myFixture.configureByText("a.groovy", "new Ccc(ss<caret>)");

    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "sss1", "sss2", "sss3", "sss4");
  }

  public void testRenameProperty() {
    PsiFile groovyFile = myFixture.addFileToProject("g.groovy", "new Aaa(sss: '1')");
    myFixture.configureByText("Aaa.java", """
      
      public class Aaa {
        public String sss<caret>;
      }
      """);
    myFixture.renameElementAtCaret("field");

    TestCase.assertEquals("new Aaa(field: '1')", groovyFile.getText());
  }

  public void testRenameMethod() {
    PsiFile groovyFile = myFixture.addFileToProject("g.groovy", "new Aaa(sss: '1')");
    myFixture.configureByText("Aaa.java", """
      
      public class Aaa {
        public void setSss<caret>(String s){}
      }
      """);
    myFixture.renameElementAtCaret("setField");

    TestCase.assertEquals("new Aaa(field: '1')", groovyFile.getText());
  }

  private void doTestHighlighting(String text) {
    myFixture.enableInspections(GroovyAssignabilityCheckInspection.class);
    myFixture.enableInspections(GroovyUncheckedAssignmentOfMemberOfRawTypeInspection.class);

    myFixture.configureByText("a.groovy", text);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testCheckingTypeString() {
    doTestHighlighting("""
                         
                         class Ccc {
                           String foo
                         
                           static {
                         println(new Ccc(foo: 123))
                         println(new Ccc(foo: 123L))
                         println(new Ccc(foo: 123f))
                         println(new Ccc(foo: 'text'))
                         println(new Ccc(foo: null))
                         println(new Ccc(foo: new Object()))
                         println(new Ccc(foo: new Object[1]))
                         println(new Ccc(foo: Collections.singletonList("as")))
                           }
                         }
                         """);
  }

  public void testCheckingTypeInt() {
    doTestHighlighting("""
                         
                         class Ccc {
                           int foo
                         
                           static {
                         println(new Ccc(foo: 123))
                         println(new Ccc(foo: new Integer(123)))
                         println(new Ccc(foo: 123L))
                         println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'Boolean'">true</warning>))
                         println(new Ccc(foo: 123f))
                         println(new Ccc(foo: new Float(123f)))
                         println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'String'">'1111'</warning>))
                         println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'null'">null</warning>))
                         println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'Object'">new Object()</warning>))
                         println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'Object[]'">new Object[1]</warning>))
                         println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'List<String>'">Collections.singletonList("as")</warning>))
                           }
                         }
                         """);
  }

  public void testCheckingTypeInteger() {
    doTestHighlighting("""
                         
                         class Ccc {
                           Integer foo
                         
                           static {
                         println(new Ccc(foo: 123))
                         println(new Ccc(foo: new Integer(123)))
                         println(new Ccc(foo: 123L))
                         println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'Boolean'">true</warning>))
                         println(new Ccc(foo: 123f))
                         println(new Ccc(foo: new Float(123f)))
                         println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'String'">'1111'</warning>))
                         println(new Ccc(foo: null))
                         println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'Object'">new Object()</warning>))
                         println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'Object[]'">new Object[1]</warning>))
                         println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'List<String>'">Collections.singletonList("as")</warning>))
                           }
                         }
                         """);
  }

  public void testCheckingTypeList() {
    doTestHighlighting("""
                         
                         class Ccc {
                           List foo
                         
                           static {
                         println(new Ccc(foo: <warning descr="Type of argument 'foo' can not be 'Integer'">123</warning>))
                         println(new Ccc(foo: null))
                         println(new Ccc(foo: new Object[1]))
                         println(new Ccc(foo: []))
                         println(new Ccc(foo: [1,2,3]))
                         println(new Ccc(foo: Collections.singletonList("as")))
                         println(new Ccc(foo: Collections.singletonList(1)))
                           }
                         }
                         """);
  }

  public void testCheckingTypeGeneric() {
    myFixture.addFileToProject("Ccc.groovy", """
      
      class Ccc<T> {
        public void setFoo(T t) {}
      }
      
      class CccMap extends Ccc<Map> {}
      class CccList extends Ccc<ArrayList> {}
      """);

    doTestHighlighting("""
                         println(new CccMap(foo: [:]))
                         println(new CccList(foo: []))
                         
                         println(new CccMap(foo: <warning descr="Cannot instantiate interface 'Map'">[]</warning>))
                         println(new CccList(foo: [:])) // new ArrayList()
                         """);
  }

  public void testCompletionFieldClosureParam() {
    doTestCompletion("""
                       
                       class Test {
                         def field = {attr ->
                           return attr.sss1 + attr.sss2
                         };
                       
                         {
                           field(ss<caret>)
                         }
                       }
                       """, true);
  }

  public void testCompletionVariableClosureParam() {
    doTestCompletion("""
                       
                       class Test {
                         {
                           def variable = {attr ->
                             return attr.sss1 + attr.sss2
                           }
                       
                           variable(ss<caret>)
                         }
                       }
                       """, true);
  }

  public void testCompletionReturnMethod() {
    myFixture.addFileToProject("Ccc.groovy", """
      
      class Ccc {
        String sss1;
        String sss2;
      }
      """);

    doTestCompletion("""
                       
                       class Test {
                         public Ccc createC(HashMap m) {
                           return new Ccc(m)
                         }
                       
                         public static void main(String[] args) {
                           println(new Test().createC(<caret>))
                         }
                       }
                       """, true);
  }

  public void test_completion_within_some_map() {
    doTestCompletionWithinMap("[<caret>]", "[bar: <caret>]");
  }

  public void test_completion_within_map_in_argument_list() {
    doTestCompletionWithinMap("foo(1, 2, 3, [<caret>])", "foo(1, 2, 3, [bar: <caret>])");
  }

  private void doTestCompletionWithinMap(String text, String text2) {
    GroovyNamedArgumentProvider.EP_NAME.getPoint().registerExtension(new GroovyNamedArgumentProvider() {
      @NotNull
      @Override
      public Map<String, NamedArgumentDescriptor> getNamedArguments(@NotNull GrListOrMap literal) {
        LinkedHashMap<String, NamedArgumentDescriptor> map = new LinkedHashMap<String, NamedArgumentDescriptor>(2);
        map.put("foo", NamedArgumentDescriptor.SIMPLE_NORMAL);
        map.put("bar", NamedArgumentDescriptor.SIMPLE_NORMAL);
        return map;
      }
    }, myFixture.getTestRootDisposable());

    myFixture.configureByText("_.groovy", text);
    myFixture.completeBasic();

    List<String> lookUpStrings1 = myFixture.getLookupElementStrings();
    assertContainsElements(lookUpStrings1, "foo");
    assertContainsElements(lookUpStrings1, "bar");

    myFixture.type("ba\n");
    if (text2.isEmpty()) myFixture.checkResult(text2);

    myFixture.type(",");
    myFixture.completeBasic();
    List<String> lookUpStrings2 = myFixture.getLookupElementStrings();
    assertContainsElements(lookUpStrings2, "foo");
    assertDoesntContain(lookUpStrings2, "bar");
  }
}
