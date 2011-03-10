/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Maxim.Medvedev
 */
class GrCompletionWithLibraryTest extends GroovyCompletionTestBase {
  public static final DefaultLightProjectDescriptor GROOVY_17_PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
      final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary("GROOVY").getModifiableModel();
      final VirtualFile groovyJar = JarFileSystem.getInstance().refreshAndFindFileByPath(TestUtils.getMockGroovy1_7LibraryName() + "!/");
      modifiableModel.addRoot(groovyJar, OrderRootType.CLASSES);
      modifiableModel.commit();
    }
  };

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GROOVY_17_PROJECT_DESCRIPTOR;
  }

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/completion/";
  }

  public void testCategoryMethod() {doBasicTest()}
  public void testCategoryProperty() {doBasicTest()}
  public void testMultipleCategories() {doBasicTest()}
  public void testCategoryForArray() {doBasicTest()}

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
  public void testEachMethodForEnumRanges() throws Throwable {doBasicTest();}

  public void testTwoMethodWithSameName() {
    doVariantableTest "fooo", "fooo"
  }

  public void testIteratorNext() {
    doVariantableTest "next", "notify", "notifyAll"
  }

  public void testGstringExtendsString() {
    myFixture.testCompletionVariants getTestName(false)+".groovy", "stripIndent", "stripIndent", "stripIndentFromLine"
  }

  public void testCompletionInEachClosure() {
    myFixture.testCompletionVariants getTestName(false)+".groovy", "intValue", "intdiv", "intdiv"
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
    assertOrderedEquals myFixture.lookupElementStrings,"""\
fromThis
fromThis2
fromThis3
fromThis4
fromThis5
overridden
thisProp
class
equals
fromSuper
fromSuper2
getProperty
hashCode
invokeMethod
metaClass
metaPropertyValues
notify
notifyAll
properties
setProperty
superProp
toString
wait
wait
wait
with\
""".split('\n')
  }

}
