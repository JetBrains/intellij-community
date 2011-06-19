package org.jetbrains.plugins.groovy.spock;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class SpockVariableDescriptor {

  private final String myName;

  private final PsiElement myNavigationElement;

  private List<GrExpression> myExpressions;
  private List<GrExpression> myExpressionsOfCollection;

  private PsiVariable myVariable;

  private static final RecursionGuard guard = RecursionManager.createGuard("SpockVariableDescriptor");

  public SpockVariableDescriptor(PsiElement navigationElement, String name) {
    myName = name;
    myNavigationElement = navigationElement;
    myExpressions = new ArrayList<GrExpression>();
  }

  public SpockVariableDescriptor addExpression(@Nullable GrExpression expression) {
    myExpressions.add(expression);
    return this;
  }

  public SpockVariableDescriptor addExpressionOfCollection(@Nullable GrExpression expression) {
    if (myExpressionsOfCollection == null) {
      myExpressionsOfCollection = new ArrayList<GrExpression>();
    }

    myExpressionsOfCollection.add(expression);
    return this;
  }

  public String getName() {
    return myName;
  }

  public PsiElement getNavigationElement() {
    return myNavigationElement;
  }

  public PsiVariable getVariable() {
    if (myVariable == null) {
      final PsiManager manager = myNavigationElement.getManager();

      PsiType type = guard.doPreventingRecursion(this, new Computable<PsiType>() {
        @Override
        public PsiType compute() {
          PsiType res = null;

          for (GrExpression expression : myExpressions) {
            if (expression == null) continue;

            res = TypesUtil.getLeastUpperBoundNullable(res, expression.getType(), manager);
          }

          if (myExpressionsOfCollection != null) {
            for (GrExpression expression : myExpressionsOfCollection) {
              if (expression == null) continue;

              PsiType listType = expression.getType();
              PsiType type = PsiUtil.extractIterableTypeParameter(listType, true);

              if (type == null) {
                if (listType == null) continue;

                if (listType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
                  type = PsiType.getJavaLangString(expression.getManager(), expression.getResolveScope());
                }
              }

              res = TypesUtil.getLeastUpperBoundNullable(res, type, manager);
            }
          }

          return res;
        }
      });

      if (type == null) {
        type = PsiType.getJavaLangObject(manager, myNavigationElement.getResolveScope());
      }

      myVariable = new SpockVariable(null, manager, myName, type, myNavigationElement);
    }

    return myVariable;
  }

  private static class SpockVariable extends GrLightVariable {
    public SpockVariable(@Nullable PsiModifierList modifierList,
                         PsiManager manager,
                         @NonNls String name,
                         @NotNull PsiType type,
                         @NotNull PsiElement navigationElement) {
      super(modifierList, manager, name, type, navigationElement);
    }

    @Override
    public boolean isEquivalentTo(PsiElement another) {
      return super.isEquivalentTo(another)
             || (another instanceof SpockVariable && getNavigationElement() == another.getNavigationElement());
    }
  }

  @Override
  public String toString() {
    return myName;
  }
}
