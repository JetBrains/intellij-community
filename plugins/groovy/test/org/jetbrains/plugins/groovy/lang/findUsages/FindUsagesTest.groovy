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

package org.jetbrains.plugins.groovy.lang.findUsages

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.find.FindManager
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesManager
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.find.impl.FindManagerImpl
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.searches.SuperMethodsSearch
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import com.intellij.util.Query
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author ven
 */
public class FindUsagesTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    return "${TestUtils.testDataPath}findUsages/${getTestName(true)}/";
  }

  private void doConstructorTest(String filePath, int expectedCount) throws Throwable {
    myFixture.configureByFile(filePath);
    final PsiElement elementAt = myFixture.file.findElementAt(myFixture.editor.caretModel.offset);
    final PsiMethod method = PsiTreeUtil.getParentOfType(elementAt, PsiMethod.class);
    assertNotNull(method);
    assertTrue(method.constructor);
    final Query<PsiReference> query = ReferencesSearch.search(method);

    assertEquals(expectedCount, query.findAll().size());
  }

  public void testDerivedClass() throws Throwable {
    myFixture.configureByFiles("p/B.java", "A.groovy");
    final PsiElement elementAt = myFixture.file.findElementAt(myFixture.editor.caretModel.offset);
    final PsiClass clazz = PsiTreeUtil.getParentOfType(elementAt, PsiClass.class);
    assertNotNull(clazz);

    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myFixture.project);
    final Query<PsiClass> query = DirectClassInheritorsSearch.search(clazz, projectScope);

    assertEquals(1, query.findAll().size());
  }

  public void testConstructor1() throws Throwable {
    doConstructorTest("A.groovy", 2);
  }

  public void testConstructorUsageInNewExpression() throws Throwable {
    myFixture.configureByFile("ConstructorUsageInNewExpression.groovy");
    final PsiElement resolved = TargetElementUtil.findTargetElement(myFixture.editor, TargetElementUtil.instance.referenceSearchFlags);
    assertNotNull("Could not resolve reference", resolved);
    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myFixture.project);
    assertEquals(2, MethodReferencesSearch.search((PsiMethod)resolved, projectScope, true).findAll().size());
    assertEquals(4, MethodReferencesSearch.search((PsiMethod)resolved, projectScope, false).findAll().size());
  }

  public void testGotoConstructor() throws Throwable {
    myFixture.configureByFile("GotoConstructor.groovy");
    final PsiElement target = TargetElementUtil.findTargetElement(myFixture.editor, TargetElementUtil.instance.referenceSearchFlags);
    assertNotNull(target);
    assertInstanceOf(target, PsiMethod.class);
    assertTrue(((PsiMethod)target).constructor);
    assertTrue(((PsiMethod)target).parameterList.parametersCount == 0);
  }

  public void testSetter1() throws Throwable {
    doTestImpl("A.groovy", 2);
  }

  public void testGetter1() throws Throwable {
    doTestImpl("A.groovy", 1);
  }

  public void testProperty1() throws Throwable {
    doTestImpl("A.groovy", 2);
  }

  public void testProperty2() throws Throwable {
    doTestImpl("A.groovy", 1);
  }

  public void testEscapedReference() throws Throwable {
    doTestImpl("A.groovy", 1);
  }

  public void testKeywordPropertyName() throws Throwable {
    doTestImpl("A.groovy", 1);
  }

  public void testTypeAlias() throws Throwable {
    doTestImpl("A.groovy", 2);
  }

  public void testMethodAlias() throws Throwable {
    doTestImpl("A.groovy", 2);
  }

  public void testAliasImportedProperty() throws Throwable {
    myFixture.addFileToProject("Abc.groovy", "class Abc {static def foo}");
    doTestImpl("A.groovy", 5);
  }

  public void testGetterWhenAliasedImportedProperty() throws Throwable {
    myFixture.addFileToProject("Abc.groovy", "class Abc {static def foo}");
    doTestImpl("A.groovy", 5);
  }

  public void testForInParameter() throws Throwable {
    doTestImpl("A.groovy", 1);
  }

  public void testSyntheticParameter() throws Throwable {
    doTestImpl("A.groovy", 1);
  }

  public void testOverridingMethodUsage() throws Throwable {
    doTestImpl("OverridingMethodUsage.groovy", 2);
  }

  public void testDynamicUsages() {
    doTestImpl("DynamicUsages.groovy", 2);
  }

  public void testDynamicCallExpressionUsages() {
    doTestImpl("DynamicCallExpressionUsages.groovy", 2);
  }

  public void testAnnotatedMemberSearch() throws Throwable {

    final PsiReference ref = myFixture.getReferenceAtCaretPosition("A.groovy");
    assertNotNull("Did not find reference", ref);
    final PsiElement resolved = ref.resolve();
    assertNotNull("Could not resolve reference", resolved);

    final Query<PsiReference> query;
    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myFixture.getProject());
    if (resolved instanceof PsiMethod) {
      query = MethodReferencesSearch.search((PsiMethod)resolved, projectScope, true);
    }
    else {
      query = ReferencesSearch.search(resolved, projectScope);
    }

    assertEquals(1, query.findAll().size());
  }

  private void doTestImpl(String filePath, int expectedUsagesCount) {
    myFixture.configureByFile(filePath);
    assertUsageCount(expectedUsagesCount);
  }

  private void assertUsageCount(int expectedUsagesCount) {
    final PsiElement resolved = TargetElementUtil.findTargetElement(myFixture.getEditor(),
                                                                        TargetElementUtil.getInstance().getReferenceSearchFlags());
    assertNotNull("Could not resolve reference", resolved);
    doFind(expectedUsagesCount, resolved);
  }

  private void doFind(int expectedUsagesCount, PsiElement resolved) {
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(getProject())).getFindUsagesManager();
    FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(resolved, false);
    assertNotNull(handler);
    final FindUsagesOptions options = handler.getFindUsagesOptions();
    final CommonProcessors.CollectProcessor<UsageInfo> processor = new CommonProcessors.CollectProcessor<UsageInfo>();
    for (PsiElement element : handler.getPrimaryElements()) {
      handler.processElementUsages(element, processor, options);
    }
    for (PsiElement element : handler.getSecondaryElements()) {
      handler.processElementUsages(element, processor, options);
    }
    assertEquals(expectedUsagesCount, processor.getResults().size());
  }

  public void testGdkMethod() throws Exception {
    myFixture.configureByText("a.groovy", "[''].ea<caret>ch {}");
    assertUsageCount(1);
  }

  public void testGDKSuperMethodSearch() throws Exception {
    doSuperMethodTest("T");
  }

  public void testGDKSuperMethodForMapSearch() throws Exception {
    doSuperMethodTest("Map");
  }

  public void testLabels() throws Exception {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    final GroovyFile file = (GroovyFile)myFixture.getFile();
    assertEquals(2, ReferencesSearch.search(file.getTopStatements()[0]).findAll().size());
  }

  public void testConstructorUsageInAnonymousClass() {
    doTestImpl("A.groovy", 1);
  }

  void testCapitalizedProperty1() {
    doTest(1, '''\
class A {
  def Pro<caret>p
}

new A().Prop''')
  }

  void testCapitalizedProperty2() {
    doTest(1, '''\
class A {
  def Pro<caret>p
}

new A().prop''')
  }

  void testCapitalizedProperty3() {
    doTest(0, '''\
class A {
  def pro<caret>p
}

new A().Prop''')
  }

  void testCapitalizedProperty4() {
    doTest(1, '''\
class A {
  def p<caret>rop
}

new A().prop''')
  }

  void testCategoryProperty() {
    doTest(1, '''\
class Cat {
  static def ge<caret>tFoo(Number s) {'num'}
}

use(Cat) {
  2.foo
}
''')
  }

  void "test do not report dynamic usages when argument count differs"() {
    doTest(0, '''\
class A {
    static void start<caret>sWith(String s, String s1, boolean b) {}
    def c = { it.startsWith("aaa") }
}
''')
  }

  void 'test literal names'() {
    doTest(1, '''\
def 'f<caret>oo'() {}

'foo'()
''')
  }

  void 'test literal name with escaping'() {
    doTest(1, '''\
def 'f<caret>oo \\''() {}

'foo \\''()
''')
  }

  void 'test literal name with escaping and other quotes'() {
    doTest(1, '''\
def 'f<caret>oo \\''() {}

"foo '"()
''')
  }

  //todo
  void '_test literal name with escaping and other quotes 2'() {
    doTest(1, '''\
def '<caret>\\''() {}

"'"()
''')
  }


  void testResolveBinding1() {
    doTest(2, '''\
abc = 4

print ab<caret>c
''')
  }

  void testResolveBinding3() {
    doTest(2, '''\
a<caret>bc = 4

print abc
''', )
  }

  void testResolveBinding4() {
    doTest(1, '''\
print abc

a<caret>bc = 4
''', )
  }

  void testBinding9() {
    doTest(4, '''\
a<caret>a = 5
print aa
aa = 6
print aa
''', )
  }

  void testBinding10() {
    doTest(4, '''\
aa = 5
print a<caret>a
aa = 6
print aa
''', )
  }

  void testBinding11() {
    doTest(4, '''\
aa = 5
print aa
a<caret>a = 6
print aa
''', )
  }

  void testBinding12() {
    doTest(4, '''\
aa = 5
print aa
aa = 6
print a<caret>a
''', )
  }

  void testBinding13() {
    doTest(2, '''\
aaa = 1

def foo() {
  aa<caret>a
}
''')
  }

  void testBinding14() {
    doTest(2, '''\
aa<caret>a = 1

def foo() {
  aaa
}
''')
  }

  void testBinding15() {
    doTest(1, '''\
def foo() {
  aaa
}

aa<caret>a = 1
''')
  }


  void testTraitField() {
    doTest(4, '''
trait T {
  public int fi<caret>eld = 4

  def bar() {
    print field
    print T__field
  }
}

class C implements T {
  def abc() {
    print field         //unresolved
    print T__field
  }
}


new C().T__field
new C().field             //unresolved

''')
  }

  private void doSuperMethodTest(String... firstParameterTypes) {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    final GroovyFile file = (GroovyFile)myFixture.getFile();
    final GrTypeDefinition psiClass = (GrTypeDefinition)file.getClasses()[0];
    final GrMethod method = (GrMethod)psiClass.getMethods()[0];
    final Collection<MethodSignatureBackedByPsiMethod> superMethods = SuperMethodsSearch.search(method, null, true, true).findAll();
    assertEquals(firstParameterTypes.length, superMethods.size());

    final Iterator<MethodSignatureBackedByPsiMethod> iterator = superMethods.iterator();
    for (String firstParameterType : firstParameterTypes) {
      final MethodSignatureBackedByPsiMethod methodSignature = iterator.next();
      final PsiMethod superMethod = methodSignature.getMethod();
      final String className = superMethod.getContainingClass().getName();
      assertEquals("DefaultGroovyMethods", className);
      final String actualParameterType = ((PsiClassType)methodSignature.getParameterTypes()[0]).resolve().getName();
      assertEquals(firstParameterType, actualParameterType);
    }
  }

  private void doTest(int usageCount, String text) {
    myFixture.configureByText('_.groovy', text)
    assertUsageCount(usageCount);
  }

}
