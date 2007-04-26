/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.NameHint;
import com.intellij.util.IncorrectOperationException;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.Icons;

import javax.swing.*;

/**
 * @author Ilya Sergey
 */
public abstract class GrTypeDefinitionImpl extends GroovyPsiElementImpl implements GrTypeDefinition {

  public GrTypeDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public int getTextOffset() {
    return getNameIdentifier().getTextRange().getStartOffset();
  }

  @Nullable
  public String getQualifiedName() {
    PsiElement parent = getParent();
    if (parent instanceof GrTypeDefinition) {
      return ((GrTypeDefinition) parent).getQualifiedName() + "." + getName();
    } else if (parent instanceof GroovyFile) {
      String packageName = ((GroovyFile) parent).getPackageName();
      return packageName.length() > 0 ? packageName + "." + getName() : getName();
    }

    return null;
  }

  public GrTypeParameter[] getTypeParameters() {
    return findChildrenByClass(GrTypeParameter.class);
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation(){

      public String getPresentableText() {
        return getName();
      }

      @Nullable
      public String getLocationString() {
        return "in "+ getContainingFile().getVirtualFile().getName();                  // TODO implement
      }

      @Nullable
      public Icon getIcon(boolean open) {
        return Icons.SMALLEST;
      }

      @Nullable
      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };
  }


  @NotNull
  public PsiElement getNameIdentifier() {
    PsiElement result = findChildByType(GroovyElementTypes.mIDENT);
    assert result != null;
    return result;
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("NIY");
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement psiElement, @NotNull PsiElement psiElement1) {
    if (!processor.execute(this, substitutor)) return false;
    for (final GrTypeParameter typeParameter : getTypeParameters()) {
      if (!processor.execute(typeParameter, substitutor)) return false;
    }

    return true;
  }

  private boolean process(PsiScopeProcessor processor, PsiNamedElement element) {
    NameHint nameHint = processor.getHint(NameHint.class);
    if (nameHint == null || getName().equals(nameHint.getName())) {
      return processor.execute(element, PsiSubstitutor.EMPTY);
    }
    return true;
  }

  @NotNull
  public String getName() {
    return getNameIdentifier().getText();
  }
}
