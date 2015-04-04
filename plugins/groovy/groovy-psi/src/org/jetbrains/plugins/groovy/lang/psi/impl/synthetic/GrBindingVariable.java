/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTupleExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import javax.swing.*;

/**
 * @author Max Medvedev
 */
public class GrBindingVariable extends GrLightVariable implements GrVariable {
  private final GroovyFile myFile;
  private Boolean myHasWriteAccess;

  public GrBindingVariable(final GroovyFile file, String name, Boolean isWriteAccess) {
    super(file.getManager(), name, CommonClassNames.JAVA_LANG_OBJECT, file);
    myFile = file;
    myHasWriteAccess = isWriteAccess;
  }

  @Override
  public PsiElement getContext() {
    return myFile;
  }

  @Nullable
  @Override
  public Icon getIcon(int flags) {
    return JetgroovyIcons.Groovy.Variable;
  }

  @Nullable
  @Override
  public GrExpression getInitializerGroovy() {
    return null;
  }

  @Override
  public void setType(@Nullable PsiType type) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public GrTypeElement getTypeElementGroovy() {
    return null;
  }

  @Nullable
  @Override
  public PsiType getTypeGroovy() {
    return null;
  }

  @Nullable
  @Override
  public PsiType getDeclaredType() {
    return null;
  }

  @Override
  public void setInitializerGroovy(GrExpression initializer) {
    //todo?
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public PsiElement getNameIdentifierGroovy() {
    return GroovyPsiElementFactory.getInstance(getProject()).createReferenceNameFromText(getName());
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitVariable(this);
  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {
    //todo
    throw new UnsupportedOperationException();
  }

  public boolean hasWriteAccess() {
    if (myHasWriteAccess != null) return myHasWriteAccess.booleanValue();

    myFile.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitAssignmentExpression(GrAssignmentExpression expression) {
        final GrExpression lValue = expression.getLValue();
        if (lValue instanceof GrTupleExpression) {
          for (GrExpression grExpression : ((GrTupleExpression)lValue).getExpressions()) {
            if (isRefToMe(grExpression)) {
              myHasWriteAccess = true;
              break;
            }
          }
        }
        else if (isRefToMe(lValue)) {
          myHasWriteAccess = true;
        }
        super.visitAssignmentExpression(expression);
      }

      @Override
      public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
        //don't go inside type definitions
      }

      @Override
      public void visitElement(GroovyPsiElement element) {
        if (myHasWriteAccess == null) {
          super.visitElement(element);
        }
      }
    });

    if (myHasWriteAccess == null) myHasWriteAccess = false;
    return myHasWriteAccess.booleanValue();
  }

  private boolean isRefToMe(@Nullable PsiElement element) {
    if (maybeRefToMe(element)) {
      final PsiElement resolved = ((GrReferenceExpression)element).resolve();
      if (resolved == null || resolved == this) {
        return true;
      }
    }

    return false;
  }

  private boolean maybeRefToMe(PsiElement element) {
    return element instanceof GrReferenceExpression &&
        !((GrReferenceExpression)element).isQualified() &&
        getName().equals(((GrReferenceExpression)element).getReferenceName());
  }


  public void updateWriteAccessIfNeeded(@Nullable PsiElement place) {
    if (myHasWriteAccess == null && maybeRefToMe(place)) {
      assert place != null;
      final PsiElement parent = place.getParent();
      if (parent instanceof GrAssignmentExpression && ((GrAssignmentExpression)parent).getLValue() == place) {
        myHasWriteAccess = true;
      }
    }
  }

  @Override
  public boolean isEquivalentTo(@Nullable PsiElement another) {
    return another instanceof GrBindingVariable &&
           StringUtil.equals(getName(), ((GrBindingVariable)another).getName()) &&
           getManager().areElementsEquivalent(getContainingFile(), another.getContainingFile());
  }

  @Override
  public String toString() {
    return "Binding variable";
  }
}
