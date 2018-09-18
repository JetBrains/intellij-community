// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
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

  public void testChangeMethodParameter() {
    doTest();
  }

  public void _testNotChangeReturnType() {
    doTest();
  }

  private void doTest() {
    doTest(true);
  }

  @Override
  protected void doTest(boolean exists) {
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
    WriteCommandAction.runWriteCommandAction(null, () -> ConvertMapToClassIntention
      .replaceMapWithClass(getProject(), map, psiClass, ConvertMapToClassIntention.checkForReturnFromMethod(map),
                           ConvertMapToClassIntention.checkForVariableDeclaration(map),
                           ConvertMapToClassIntention.checkForMethodParameter(map)));

    myFixture.checkResultByFile(getTestName(true) + "/Foo.groovy", getTestName(true) + "/Expected.groovy", true);
    myFixture.checkResultByFile(getTestName(true) + "/Test_after.groovy", true);
  }
}
