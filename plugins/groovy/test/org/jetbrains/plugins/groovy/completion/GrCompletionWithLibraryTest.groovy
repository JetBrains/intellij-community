/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.completion
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author Maxim.Medvedev
 */
class GrCompletionWithLibraryTest extends GroovyCompletionTestBase {
  final LightProjectDescriptor projectDescriptor = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      final Library.ModifiableModel modifiableModel = model.moduleLibraryTable.createLibrary("GROOVY").modifiableModel;
      final VirtualFile groovyJar = JarFileSystem.instance.refreshAndFindFileByPath(TestUtils.mockGroovy1_7LibraryName + "!/");
      modifiableModel.addRoot(groovyJar, OrderRootType.CLASSES);
      modifiableModel.commit();
    }
  }

  final String basePath = TestUtils.testDataPath + "groovy/completion/"

  public void testCategoryMethod() {doBasicTest()}
  public void testCategoryProperty() {doCompletionTest(null, null, '\n', CompletionType.BASIC)}
  public void testMultipleCategories() {doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.contain, 'getMd5', 'getMd52')}
  public void testMultipleCategories2() {doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.contain, 'getMd5', 'getMd52')}
  public void testMultipleCategories3() {doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.contain, 'getMd5', 'getMd52')}
  public void testCategoryForArray() {doCompletionTest(null, null, '\n', CompletionType.BASIC)}

  public void testArrayLikeAccessForList() throws Throwable {doBasicTest(); }
  public void testArrayLikeAccessForMap() throws Throwable {doBasicTest();}

  public void testEachMethodForList() throws Throwable {doBasicTest();}
  public void testEachMethodForMapWithKeyValue() throws Throwable {doBasicTest();}
  public void testEachMethodForMapWithEntry() throws Throwable {doBasicTest();}
  public void testWithMethod() throws Throwable {doBasicTest();}
  public void testInjectMethodForCollection() throws Throwable {doBasicTest();}
  public void testInjectMethodForArray() throws Throwable {doBasicTest();}
  public void testInjectMethodForMap() throws Throwable {doBasicTest();}
  public void testClosureDefaultParameterInEachMethod() throws Throwable {doBasicTest();}
  public void testEachMethodForRanges() throws Throwable {doBasicTest();}
  public void testEachMethodForEnumRanges() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy")
    myFixture.completeBasic()
    myFixture.type('\n')
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testPrintlnSpace() { checkCompletion 'print<caret>', 'l ', "println <caret>" }
  public void testHashCodeSpace() { checkCompletion 'if ("".h<caret>', ' ', 'if ("".hashCode() <caret>' }

  public void testTwoMethodWithSameName() {
    doVariantableTest "fooo", "fooo"
  }

  public void testIteratorNext() {
    doHasVariantsTest('next', 'notify')
  }

  public void testGstringExtendsString() {
    doBasicTest()
  }

  public void testCompletionInEachClosure() {
    doHasVariantsTest('intValue', 'intdiv')
  }

  public void testEllipsisTypeCompletion() {
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

  public void testBoxForinParams() {
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

  public void testEachSpace() throws Exception {
    checkCompletion '[].ea<caret>', ' ', '[].each <caret>'
  }

  public void testEachBrace() throws Exception {
    checkCompletion '[].ea<caret> {}', '\n', '[].each {<caret>}'
  }

  public void testDeclaredMembersGoFirst() {
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
metaPropertyValues
properties
superProp
class
equals
getProperty
hashCode
identity
invokeMethod
metaClass
notify
notifyAll
setProperty
toString
wait
wait
wait
with
getThisProp
setThisProp
getSuperProp
setSuperProp
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
getAt
getClass
getMetaClass
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
setMetaClass
split
sprintf
sprintf
use
use
use\
'''.split('\n')
  }

  void testListCompletionVariantsFromDGM() {
    doVariantableTest('drop', 'dropWhile')
  }

  public void testGStringConcatenationCompletion() {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "substring", "substring", "subSequence");
  }

  void testCompleteClassClashingWithGroovyUtilTuple() {
    myFixture.addClass('package p; public class Tuple {}')

    def file = myFixture.configureByText('a.groovy', 'print new Tupl<caret>')
    LookupElement[] elements = myFixture.completeBasic()
    LookupElement tuple = elements.find { it.psiElement instanceof PsiClass && it.psiElement.qualifiedName == 'p.Tuple'}
    assertNotNull(elements as String, tuple)

    LookupElement groovyUtilTuple = elements.find { it.psiElement instanceof PsiClass && it.psiElement.qualifiedName == 'groovy.lang.Tuple'}
    assertNotNull(elements as String, groovyUtilTuple)

    LookupImpl lookup = getLookup()
    lookup.setCurrentItem(tuple)

    new WriteCommandAction(myFixture.project, file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        lookup.finishLookup('\n' as char);
      }
    }.execute()

    myFixture.checkResult('''\
import p.Tuple

print new Tuple()\
''')
  }
}
