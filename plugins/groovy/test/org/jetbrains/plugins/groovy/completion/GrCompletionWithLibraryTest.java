// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Maxim.Medvedev
 */
public class GrCompletionWithLibraryTest extends GroovyCompletionTestBase {
  public void testCategoryMethod() { doBasicTest(); }

  public void testCategoryProperty() { doCompletionTest(null, null, "\n", CompletionType.BASIC); }

  public void testMultipleCategories() { doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.contain, "getMd5", "getMd52"); }

  public void testMultipleCategories2() {
    doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.contain, "getMd5", "getMd52");
  }

  public void testMultipleCategories3() {
    doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.contain, "getMd5", "getMd52");
  }

  public void testCategoryForArray() { doCompletionTest(null, null, "\n", CompletionType.BASIC); }

  public void testArrayLikeAccessForList() { doBasicTest(); }

  public void testArrayLikeAccessForMap() { doBasicTest(); }

  public void testPrintlnSpace() { checkCompletion("print<caret>", "l ", "println <caret>"); }

  public void testHashCodeSpace() { checkCompletion("if (\"\".h<caret>", " ", "if (\"\".hashCode() <caret>"); }

  public void testTwoMethodWithSameName() {
    doVariantableTest("fooo", "fooo");
  }

  public void testIteratorNext() {
    doHasVariantsTest("next", "notify");
  }

  public void testGstringExtendsString() {
    doBasicTest();
  }

  public void testEllipsisTypeCompletion() {
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

  public void testBoxForinParams() {
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

  public void testEachSpace() {
    checkCompletion("[].ea<caret>", " ", "[].each <caret>");
  }

  public void testEachBrace() {
    checkCompletion("[].ea<caret> {}", "\n", "[].each {<caret>}");
  }

  public void testDeclaredMembersGoFirst() {
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
    UsefulTestCase.assertOrderedEquals(myFixture.getLookupElementStrings(), """
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
      getClass""".split("\n"));
  }

  public void testListCompletionVariantsFromDGM() {
    doVariantableTest("drop", "dropWhile");
  }

  public void testGStringConcatenationCompletion() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "substring", "substring", "subSequence");
  }

  public void testCompleteClassClashingWithGroovyUtilTuple() {
    myFixture.addClass("package p; public class Tuple {}");

    myFixture.configureByText("a.groovy", "print new Tupl<caret>");
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
    TestCase.assertNotNull(msg.toString(), tuple);
    TestCase.assertNotNull(msg.toString(), groovyUtilTuple);
    getLookup().finishLookup('\n', tuple);

    myFixture.checkResult("""
                            import p.Tuple
                            
                            print new Tuple()""");
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_1_7;
  }

  @Override
  public final @NotNull String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/completion/";
  }
}
