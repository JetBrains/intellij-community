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
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.intentions.conversions.ConvertMapToClassIntention;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class ConvertMapToClassTest extends GrIntentionTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/convertMapToClass/";
  }

  public void testSimple() {
    doTest();
  }

  public void testVariableTypeReplace() {
    doTest();
  }

  public void testBadCase() {
    doTest(false);
  }

  public void testChangeReturnType() {
    doTest();
  }

  public void _testNotChangeReturnType() {
    doTest();
  }

  private void doTest() {
    doTest(true);
  }

  private void doTest(boolean exists) {
    myFixture.configureByFile(getTestName(true) + "/Test.groovy");
    String hint = GroovyIntentionsBundle.message("convert.map.to.class.intention.name");
    final List<IntentionAction> list = myFixture.filterAvailableIntentions(hint);
    if (!exists) {
      assertEmpty(list);
      return;
    }
    assertOneElement(list);
    final PsiElement element = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    final GrListOrMap map = PsiTreeUtil.getParentOfType(element, GrListOrMap.class);
    assertNotNull(map);
    final GrTypeDefinition foo = ConvertMapToClassIntention.createClass(getProject(), map.getNamedArguments(), "", "Foo");
    myFixture.addFileToProject(getTestName(true) + "/Foo.groovy", foo.getContainingFile().getText());
    final PsiClass psiClass = myFixture.findClass("Foo");
    ConvertMapToClassIntention.replaceMapWithClass(getProject(), map, psiClass);
    myFixture.checkResultByFile(getTestName(true) + "/Foo.groovy", getTestName(true) + "/Expected.groovy", true);
    myFixture.checkResultByFile(getTestName(true) + "/Test_after.groovy", true);
  }
}
