// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyUncheckedAssignmentOfMemberOfRawTypeInspection;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GroovyMapAttributeTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_1_7);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private void doTestCompletion(String fileText, boolean exists) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", fileText);
    LookupElement[] res = myFixture.completeBasic();

    assertNotNull(res);

    Set<String> variants = new HashSet<String>();

    for (LookupElement element : res) {
      variants.add(element.getLookupString());
    }

    if (exists) {
      assertTrue(variants.contains("sss1"));
      assertTrue(variants.contains("sss2"));
    }
    else {
      assertFalse(variants.contains("sss1"));
      assertFalse(variants.contains("sss2"));
    }
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testConstructorInJavaClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testRenameProperty() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile groovyFile = myFixture.addFileToProject("g.groovy", "new Aaa(sss: '1')");
    myFixture.configureByText("Aaa.java", """
    
      public class Aaa {
        public String sss<caret>;
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> myFixture.renameElementAtCaret("field"));

    assertEquals("new Aaa(field: '1')", groovyFile.getText());
  }

  @Test
  public void testRenameMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile groovyFile = myFixture.addFileToProject("g.groovy", "new Aaa(sss: '1')");
    myFixture.configureByText("Aaa.java", """
    
      public class Aaa {
        public void setSss<caret>(String s){}
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> myFixture.renameElementAtCaret("setField"));

    assertEquals("new Aaa(field: '1')", groovyFile.getText());
  }

  private void doTestHighlighting(String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(GroovyAssignabilityCheckInspection.class);
    myFixture.enableInspections(GroovyUncheckedAssignmentOfMemberOfRawTypeInspection.class);

    myFixture.configureByText("a.groovy", text);
    myFixture.checkHighlighting(true, false, true);
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testCheckingTypeGeneric() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
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

  @Test
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

  @Test
  public void testCompletionReturnMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void test_completion_within_some_map() {
    doTestCompletionWithinMap("[<caret>]", "[bar: <caret>]");
  }

  @Test
  public void test_completion_within_map_in_argument_list() {
    doTestCompletionWithinMap("foo(1, 2, 3, [<caret>])", "foo(1, 2, 3, [bar: <caret>])");
  }

  private void doTestCompletionWithinMap(String text, String text2) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    assertThat(lookUpStrings1).contains("foo");
    assertThat(lookUpStrings1).contains("bar");

    myFixture.type("ba\n");
    if (text2.isEmpty()) myFixture.checkResult(text2);

    myFixture.type(",");
    myFixture.completeBasic();
    List<String> lookUpStrings2 = myFixture.getLookupElementStrings();
    assertThat(lookUpStrings2).contains("foo");
    assertThat(lookUpStrings2).doesNotContain("bar");
  }
}
