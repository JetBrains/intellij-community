package org.jetbrains.plugins.groovy.cucumber;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * @author Max Medvedev
 */
public class GrCucumberUtil {

  public static final String[] STEPS = new String[]{"Given", "Then", "And", "But", "When"};

  public static boolean isStepDefinition(PsiElement element) {
    return element instanceof GrMethodCall &&
           getCucumberStepRef((GrMethodCall)element) != null &&
           getCucumberDescription((GrMethodCall)element) != null;
  }


  @Nullable
  public static String getPatternFromStepDefinition(GrAnnotation stepAnnotation) {
    String result = null;
    if (stepAnnotation.getParameterList().getAttributes().length > 0) {
      final PsiElement annotationValue = stepAnnotation.getParameterList().getAttributes()[0].getValue();
      if (annotationValue != null) {
        final PsiElement patternLiteral = annotationValue.getFirstChild();
        if (patternLiteral != null) {
          final String patternContainer = patternLiteral.getText();
          result = patternContainer.substring(1, patternContainer.length() - 1).replace("\\\\", "\\");
        }
      }
    }
    return result;
  }

  @Nullable
  public static GrReferenceExpression getCucumberStepRef(final GrMethodCall stepDefinition) {
    return ApplicationManager.getApplication().runReadAction(new NullableComputable<GrReferenceExpression>() {
      @Override
      public GrReferenceExpression compute() {
        GrExpression invoked = stepDefinition.getInvokedExpression();
        if (!(invoked instanceof GrReferenceExpression) || ((GrReferenceExpression)invoked).isQualified()) return null;

        String name = ((GrReferenceExpression)invoked).getName();
        if (!ArrayUtil.contains(name, STEPS)) return null;

        return (GrReferenceExpression)invoked;
      }
    });
  }

  @Nullable
  public static String getCucumberDescription(final GrMethodCall stepDefinition) {
    return ApplicationManager.getApplication().runReadAction(new NullableComputable<String>() {
      @Nullable
      @Override
      public String compute() {
        GrArgumentList argumentList = stepDefinition.getArgumentList();
        if (argumentList == null) return null;

        GroovyPsiElement[] arguments = argumentList.getAllArguments();
        if (arguments.length != 1) return null;

        GroovyPsiElement arg = arguments[0];
        if (!(arg instanceof GrUnaryExpression && ((GrUnaryExpression)arg).getOperationTokenType() == GroovyTokenTypes.mBNOT)) return null;

        GrExpression operand = ((GrUnaryExpression)arg).getOperand();
        if (!(operand instanceof GrLiteral)) return null;

        Object value = ((GrLiteral)operand).getValue();
        return value instanceof String ? (String)value : null;
      }
    });
  }
}
