// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiClass
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Maxim.Medvedev
 */
class GrCompletionWithLibraryTest extends GroovyCompletionTestBase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_1_7

  final String basePath = TestUtils.testDataPath + "groovy/completion/"

  void testCategoryMethod() { doBasicTest() }

  void testCategoryProperty() { doCompletionTest(null, null, '\n', CompletionType.BASIC) }

  void testMultipleCategories() { doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.contain, 'getMd5', 'getMd52') }

  void testMultipleCategories2() { doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.contain, 'getMd5', 'getMd52') }

  void testMultipleCategories3() { doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.contain, 'getMd5', 'getMd52') }

  void testCategoryForArray() { doCompletionTest(null, null, '\n', CompletionType.BASIC) }

  void testArrayLikeAccessForList() throws Throwable { doBasicTest() }

  void testArrayLikeAccessForMap() throws Throwable { doBasicTest() }

  void testPrintlnSpace() { checkCompletion 'print<caret>', 'l ', "println <caret>" }

  void testHashCodeSpace() { checkCompletion 'if ("".h<caret>', ' ', 'if ("".hashCode() <caret>' }

  void testTwoMethodWithSameName() {
    doVariantableTest "fooo", "fooo"
  }

  void testIteratorNext() {
    doHasVariantsTest('next', 'notify')
  }

  void testGstringExtendsString() {
    doBasicTest()
  }

  void testEllipsisTypeCompletion() {
    myFixture.configureByText "a.groovy", """
def foo(def... args) {
  args.si<caret>
}"""
    myFixture.completeBasic()
    myFixture.checkResult """
def foo(def... args) {
  args.size()
}"""
  }

  void testBoxForinParams() {
    myFixture.configureByText "A.groovy", """
for (def ch: "abc".toCharArray()) {
  print ch.toUpperCa<caret>
}"""
    myFixture.completeBasic()
    myFixture.checkResult """
for (def ch: "abc".toCharArray()) {
  print ch.toUpperCase()
}"""
  }

  void testEachSpace() throws Exception {
    checkCompletion '[].ea<caret>', ' ', '[].each <caret>'
  }

  void testEachBrace() throws Exception {
    checkCompletion '[].ea<caret> {}', '\n', '[].each {<caret>}'
  }

  void testDeclaredMembersGoFirst() {
    myFixture.configureByText "a.groovy", """
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
    """
    myFixture.completeBasic()
    assertOrderedEquals myFixture.lookupElementStrings, '''\
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
getClass\
'''.split('\n')
  }

  void testListCompletionVariantsFromDGM() {
    doVariantableTest('drop', 'dropWhile')
  }

  void testGStringConcatenationCompletion() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "substring", "substring", "subSequence")
  }

  void testCompleteClassClashingWithGroovyUtilTuple() {
    myFixture.addClass('package p; public class Tuple {}')

    def file = myFixture.configureByText('a.groovy', 'print new Tupl<caret>')
    LookupElement[] elements = myFixture.completeBasic()
    LookupElement tuple = elements.find { it.psiElement instanceof PsiClass && it.psiElement.qualifiedName == 'p.Tuple'}
    assertNotNull(elements as String, tuple)

    LookupElement groovyUtilTuple = elements.find { it.psiElement instanceof PsiClass && it.psiElement.qualifiedName == 'groovy.lang.Tuple'}
    assertNotNull(elements as String, groovyUtilTuple)

    lookup.finishLookup('\n' as char, tuple)

    myFixture.checkResult('''\
import p.Tuple

print new Tuple()\
''')
  }
}
