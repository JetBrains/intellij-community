package org.jetbrains.plugins.groovy.lang.dynamic;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicMethodFix;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicPropertyFix;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.MyPair;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DClassElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DRootElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 01.04.2008
 */                                  
public class DynamicTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "dynamic/";
  }

  public void testMethod() throws Throwable {
    final GrReferenceExpression referenceExpression = doDynamicFix();

    final PsiType[] psiTypes = PsiUtil.getArgumentTypes(referenceExpression, false, false);
    final String[] methodArgumentsNames = QuickfixUtil.getMethodArgumentsNames(getProject(), psiTypes);
    final List<MyPair> pairs = QuickfixUtil.swapArgumentsAndTypes(methodArgumentsNames, psiTypes);

    assertNotNull(getDClassElement().getMethod(referenceExpression.getName(), QuickfixUtil.getArgumentsTypes(pairs)));
  }

  @NotNull
  private DClassElement getDClassElement() {
    final DRootElement rootElement = DynamicManager.getInstance(getProject()).getRootElement();
    final DClassElement classElement = rootElement.getClassElement(getTestName(false));
    assertNotNull(classElement);
    return classElement;
  }

  public void testProperty() throws Throwable {
    final String name = doDynamicFix().getName();
    assert getDClassElement().getPropertyByName(name) != null;
  }

  private GrReferenceExpression doDynamicFix() throws Throwable {
    final List<IntentionAction> actions = myFixture.getAvailableIntentions(getTestName(false) + ".groovy");

    DynamicPropertyFix dynamicFix = ContainerUtil.findInstance(actions, DynamicPropertyFix.class);
    if (dynamicFix != null) {
      dynamicFix.invoke(getProject());
      return dynamicFix.getReferenceExpression();
    }
    else {
      final DynamicMethodFix fix = ContainerUtil.findInstance(actions, DynamicMethodFix.class);
      fix.invoke(getProject());
      return fix.getReferenceExpression();
    }
  }

}
