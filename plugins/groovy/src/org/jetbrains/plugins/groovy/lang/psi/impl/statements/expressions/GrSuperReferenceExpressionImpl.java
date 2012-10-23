
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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSuperReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;

/**
 * @author ilyas
 */
public class GrSuperReferenceExpressionImpl extends GrThisSuperReferenceExpressionBase implements GrSuperReferenceExpression {

  private static final NullableFunction<GrSuperReferenceExpressionImpl, PsiType> TYPE_CALCULATOR =
    new NullableFunction<GrSuperReferenceExpressionImpl, PsiType>() {
      @Override
      public PsiType fun(GrSuperReferenceExpressionImpl ref) {
        final GrReferenceExpression qualifier = ref.getQualifier();
        if (qualifier == null) {
          GroovyPsiElement context = PsiTreeUtil.getParentOfType(ref, GrTypeDefinition.class, GroovyFileBase.class);
          if (context instanceof GrTypeDefinition) {
            final PsiClass superClass = ((GrTypeDefinition)context).getSuperClass();
            if (superClass != null) {
              return JavaPsiFacade.getInstance(ref.getProject()).getElementFactory().createType(superClass);
            }
          }
          else if (context instanceof GroovyFileBase) {
            PsiClass scriptClass = ((GroovyFileBase)context).getScriptClass();
            if (scriptClass != null) {
              PsiClass superClass = scriptClass.getSuperClass();
              if (superClass != null) {
                return JavaPsiFacade.getInstance(ref.getProject()).getElementFactory().createType(superClass);
              }
            }
            return GrClassImplUtil.getGroovyObjectType(ref);
          }
        }
        else {
          final PsiElement resolved = qualifier.resolve();
          if (resolved instanceof PsiClass) {
            return ref.getSuperType((PsiClass)resolved);
          }
        }
        return null;
      }
    };

  public GrSuperReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
  }

  public String toString() {
    return "'super' reference expression";
  }

  public PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, TYPE_CALCULATOR);
      
  }

  @Nullable
  private PsiType getSuperType(PsiClass aClass) {
    if (aClass.isInterface()) {
      return PsiType.getJavaLangObject(getManager(), getResolveScope());
    }
    if (aClass instanceof GrAnonymousClassDefinition) {
      final PsiClassType baseClassType = ((GrAnonymousClassDefinition)aClass).getBaseClassType();
      final PsiClass psiClass = baseClassType.resolve();
      if (psiClass != null && !psiClass.isInterface()) {
        return baseClassType;
      }

      return PsiType.getJavaLangObject(getManager(), getResolveScope());
    }

    if (CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) return null;
    PsiClassType[] superTypes = aClass.getExtendsListTypes();
    if (superTypes.length == 0) {
      return PsiType.getJavaLangObject(getManager(), getResolveScope());
    }

    return superTypes[0];
  }

  @Override
  public String getReferenceName() {
    return "super";
  }
}
