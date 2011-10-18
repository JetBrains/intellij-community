
/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ilyas
 */
public class GrThisReferenceExpressionImpl extends GrThisSuperReferenceExpressionBase implements GrThisReferenceExpression {

  private static final NullableFunction<GrThisReferenceExpressionImpl,PsiType> TYPE_CALCULATOR =
    new NullableFunction<GrThisReferenceExpressionImpl, PsiType>() {
      @Override
      public PsiType fun(GrThisReferenceExpressionImpl ref) {
        final GrReferenceExpression qualifier = ref.getQualifier();
        if (qualifier == null) {
          GroovyPsiElement context = PsiUtil.getFileOrClassContext(ref);
          if (context instanceof GrTypeDefinition) {
            return ref.createType((PsiClass)context);
          }
          else if (context instanceof GroovyFileBase) {
            return ref.createType(((GroovyFileBase)context).getScriptClass());
          }
        }
        else {
          final PsiElement resolved = qualifier.resolve();
          if (resolved instanceof PsiClass) {
            return JavaPsiFacade.getElementFactory(ref.getProject()).createType((PsiClass)resolved);
          }
          else {
            try {
              return JavaPsiFacade.getElementFactory(ref.getProject()).createTypeFromText(qualifier.getText(), ref);
            }
            catch (IncorrectOperationException e) {
              return null;
            }
          }
        }

        return null;
      }
    };

  public GrThisReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitThisExpression(this);
  }

  public String toString() {
    return "'this' reference expression";
  }

  public PsiType getType() {
    return GroovyPsiManager.getInstance(getProject()).getType(this, TYPE_CALCULATOR);
  }

  private PsiType createType(PsiClass context) {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    if (PsiUtil.isInStaticContext(this)) {
      return elementFactory.createTypeFromText(CommonClassNames.JAVA_LANG_CLASS + "<" + context.getName() + ">", this);
      //in case of anonymous class this code don't matter because anonymous classes can't have static methods
    }
    return elementFactory.createType(context);
  }

  @NotNull
  @Override
  public String getReferenceName() {
    return "this";
  }

  @Override
  protected PsiElement resolveInner() {
    final PsiElement resolved = super.resolveInner();
    if (resolved != null) return resolved;
    final GrReferenceExpression qualifier = getQualifier();
    if (qualifier != null) {
      return qualifier.resolve();
    }

    final GroovyPsiElement context = PsiUtil.getFileOrClassContext(this);
    if (context instanceof GrTypeDefinition) {
      return context;
    }
    else if (context instanceof GroovyFileBase) {
      return ((GroovyFileBase)context).getScriptClass();
    }
    return null;
  }

}
