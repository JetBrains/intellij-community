/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.lang.refactoring.ReferencesToInlineSearcher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrClassSubstitution;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author ilyas
 */
public class GroovyInlineHandler implements InlineHandler, ReferencesToInlineSearcher {

  private static final Logger LOG = Logger.getInstance(GroovyInlineHandler.class);

  @Nullable
  public Settings prepareInlineElement(final PsiElement element, Editor editor, boolean invokedOnReference) {
    if (element instanceof GrVariable) {
      if (GroovyRefactoringUtil.isLocalVariable((GrVariable)element)) {
        return GroovyInlineVariableUtil.inlineLocalVariableSettings((GrVariable)element, editor);
      }
      else if (element instanceof GrField) {
        return GroovyInlineVariableUtil.inlineFieldSettings((GrField)element, editor, invokedOnReference);
      }
    }
    else if (element instanceof GrAccessorMethod) {
      return GroovyInlineVariableUtil.inlineFieldSettings(((GrAccessorMethod)element).getProperty(), editor, invokedOnReference);
    }
    else if (element instanceof GrMethod) {
      return GroovyInlineMethodUtil.inlineMethodSettings((GrMethod)element, editor, invokedOnReference);
    }
    else {
      if (element instanceof GrTypeDefinition || element instanceof GrClassSubstitution) {
        return null;      //todo inline to anonymous class, push members from super class
      }
    }

    if (element instanceof PsiMember) {
      //dummy, todo implement
      return new Settings() {
        public boolean isOnlyOneReferenceToInline() {
          return true;
        }
      };
    }
    return null;
  }

  public void removeDefinition(PsiElement element, Settings settings) {
    if (element instanceof GrAccessorMethod) {
      element = ((GrAccessorMethod)element).getProperty();
    }
    final PsiElement owner = element.getParent().getParent();
    if (element instanceof GrVariable && owner instanceof GrVariableDeclarationOwner) {
      ((GrVariableDeclarationOwner)owner).removeVariable(((GrVariable)element));
    }
    if (element instanceof GrMethod) {
      element.delete();
    }
  }

  @Nullable
  public Inliner createInliner(PsiElement element, Settings settings) {
    if (element instanceof GrVariable) {
      return GroovyInlineVariableUtil.createInlinerForVariable(((GrVariable)element));
    }
    if (element instanceof GrAccessorMethod) {
      return GroovyInlineVariableUtil.createInlinerForVariable(((GrAccessorMethod)element).getProperty());
    }
    if (element instanceof GrMethod) {
      return new GroovyMethodInliner((GrMethod)element);
    }
    return null;
  }

  @Override
  public Collection<PsiReference> findReferences(PsiElement element) {
    if (element instanceof GrAccessorMethod) element = ((GrAccessorMethod)element).getProperty();

    if (!(element instanceof GrField)) {
      return ReferencesSearch.search(element).findAll();
    }

    LOG.assertTrue(((GrField)element).getSetter() == null);

    Collection<PsiReference> result = new ArrayList<PsiReference>();
    result.addAll(ReferencesSearch.search(element).findAll());

    for (GrAccessorMethod getter : ((GrField)element).getGetters()) {
      result.addAll(MethodReferencesSearch.search(getter).findAll());
    }

    return result;
  }
}

