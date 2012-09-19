package org.jetbrains.plugins.groovy.cucumber.steps;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition;
import org.jetbrains.plugins.groovy.cucumber.GrCucumberUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrStepDefinition extends AbstractStepDefinition {
  private String myText;

  public GrStepDefinition(GrMethodCall stepDefinition) {
    super(stepDefinition);

    myText = GrCucumberUtil.getCucumberDescription(stepDefinition);
  }

  @Override
  public List<String> getVariableNames() {
    PsiElement element = getElement();
    if (element instanceof GrMethodCall) {
      GrClosableBlock[] closures = ((GrMethodCall)element).getClosureArguments();
      assert closures.length == 1;
      GrParameter[] parameters = closures[0].getParameterList().getParameters();
      ArrayList<String> result = new ArrayList<String>();
      for (GrParameter parameter : parameters) {
        result.add(parameter.getName());
      }

      return result;
    }
    return Collections.emptyList();
  }

  @Override
  public String getElementText() {
    return myText;
  }
}
