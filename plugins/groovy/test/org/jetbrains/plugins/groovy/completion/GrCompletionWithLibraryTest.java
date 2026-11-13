// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import java.util.List;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.completion.GroovyCompletionFixtureKt.groovyCompletionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Maxim.Medvedev
 */
@TestApplication
public class GrCompletionWithLibraryTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_1_7);
  private final TestFixture<GroovyCompletionFixture> completion = groovyCompletionFixture(project, TestUtils.getAbsoluteTestDataPath() + "groovy/completion/");

  @Test
  public void testCategoryMethod() {
    completion.get().doBasicTest();
  }

  @Test
  public void testCategoryProperty() {
    completion.get().doCompletionTest(null, null, "\n", CompletionType.BASIC);
  }

  @Test
  public void testMultipleCategories() {
    completion.get().doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.contain, "getMd5", "getMd52");
  }

  @Test
  public void testMultipleCategories2() {
    completion.get().doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.contain, "getMd5", "getMd52");
  }

  @Test
  public void testMultipleCategories3() {
    completion.get().doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.contain, "getMd5", "getMd52");
  }

  @Test
  public void testCategoryForArray() {
    completion.get().doCompletionTest(null, null, "\n", CompletionType.BASIC);
  }

  @Test
  public void testArrayLikeAccessForList() {
    completion.get().doBasicTest();
  }

  @Test
  public void testArrayLikeAccessForMap() {
    completion.get().doBasicTest();
  }

  @Test
  public void testPrintlnSpace() {
    completion.get().checkCompletion("print<caret>", "l ", "println <caret>");
  }

  @Test
  public void testHashCodeSpace() {
    completion.get().checkCompletion("if (\"\".h<caret>", " ", "if (\"\".hashCode() <caret>");
  }

  @Test
  public void testTwoMethodWithSameName() {
    completion.get().doVariantableTest("fooo", "fooo");
  }

  @Test
  public void testIteratorNext() {
    completion.get().doHasVariantsTest("next", "notify");
  }

  @Test
  public void testGstringExtendsString() {
    completion.get().doBasicTest();
  }

  @Test
  public void testEllipsisTypeCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    myFixture.configureByText("a.groovy", """
      
      def foo(def... args) {
        args.si<caret>
      }""");
    myFixture.completeBasic();
    myFixture.checkResult("""
                            
                            def foo(def... args) {
                              args.size()
                            }""");
  }

  @Test
  public void testBoxForinParams() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    myFixture.configureByText("A.groovy", """
      
      for (def ch: "abc".toCharArray()) {
        print ch.toUpperCa<caret>
      }""");
    myFixture.completeBasic();
    myFixture.checkResult("""
                            
                            for (def ch: "abc".toCharArray()) {
                              print ch.toUpperCase()
                            }""");
  }

  @Test
  public void testEachSpace() {
    completion.get().checkCompletion("[].ea<caret>", " ", "[].each <caret>");
  }

  @Test
  public void testEachBrace() {
    completion.get().checkCompletion("[].ea<caret> {}", "\n", "[].each {<caret>}");
  }

  @Test
  public void testDeclaredMembersGoFirst() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    myFixture.configureByText("a.groovy", """
      
            class Foo {
              def superProp
              void fromSuper() {}
              void fromSuper2() {}
              void overridden() {}
            }
      
            class FooImpl extends Foo {
              def thisProp
              void overridden() {}
              void fromThis() {}
              void fromThis2() {}
              void fromThis3() {}
              void fromThis4() {}
              void fromThis5() {}
            }
      
            new FooImpl().<caret>
          \
      """);
    myFixture.completeBasic();
    runInEdtAndWait(() -> {
      assertEquals(List.of("""
        fromThis
        fromThis2
        fromThis3
        fromThis4
        fromThis5
        overridden
        thisProp
        fromSuper
        fromSuper2
        metaClass
        metaPropertyValues
        properties
        superProp
        getProperty
        invokeMethod
        setProperty
        equals
        hashCode
        toString
        class
        identity
        with
        notify
        notifyAll
        wait
        wait
        wait
        getThisProp
        setThisProp
        getSuperProp
        setSuperProp
        getMetaClass
        setMetaClass
        addShutdownHook
        any
        any
        asBoolean
        asType
        collect
        collect
        dump
        each
        eachWithIndex
        every
        every
        find
        findAll
        findIndexOf
        findIndexOf
        findIndexValues
        findIndexValues
        findLastIndexOf
        findLastIndexOf
        findResult
        findResult
        getAt
        getMetaPropertyValues
        getProperties
        grep
        hasProperty
        inject
        inspect
        is
        isCase
        iterator
        metaClass
        print
        print
        printf
        printf
        println
        println
        println
        putAt
        respondsTo
        respondsTo
        split
        sprintf
        sprintf
        use
        use
        use
        getClass""".split("\n")), myFixture.getLookupElementStrings());
    });
  }

  @Test
  public void testListCompletionVariantsFromDGM() {
    completion.get().doVariantableTest("drop", "dropWhile");
  }

  @Test
  public void testGStringConcatenationCompletion() {
    completion.get().getFixture().testCompletionVariants(completion.get().getFixture().getTestName(false) + ".groovy", "substring", "substring", "subSequence");
  }

  @Test
  public void testCompleteClassClashingWithGroovyUtilTuple() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    myFixture.addClass("package p; public class Tuple {}");

    myFixture.configureByText("a.groovy", "print new Tupl<caret>");
    runInEdtAndWait(() -> {
      LookupElement tuple = null;
      LookupElement groovyUtilTuple = null;
      StringBuilder msg = new StringBuilder();
      for (LookupElement element : myFixture.completeBasic()) {
        if (element.getPsiElement() instanceof PsiClass el) {
          switch (el.getQualifiedName()) {
            case "p.Tuple":
              tuple = element;
              break;
            case "groovy.lang.Tuple":
              groovyUtilTuple = element;
              break;
          }
        }
        if (!msg.isEmpty()) msg.append(", ");
        msg.append(element.getLookupString());
      }
      Assertions.assertNotNull(tuple, msg.toString());
      Assertions.assertNotNull(groovyUtilTuple, msg.toString());
      completion.get().getLookup().finishLookup('\n', tuple);
    });

    myFixture.checkResult("""
                            import p.Tuple
                            
                            print new Tuple()""");
  }

}
