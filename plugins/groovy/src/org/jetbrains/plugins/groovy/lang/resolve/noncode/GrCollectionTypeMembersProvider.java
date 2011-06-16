/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve.noncode;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

/**
 * @author Maxim.Medvedev
 */
public class GrCollectionTypeMembersProvider extends NonCodeMembersContributor {
  @Override
  public void processDynamicElements(final @NotNull PsiType qualifierType,
                                     final PsiScopeProcessor processor,
                                     final GroovyPsiElement place,
                                     final ResolveState state) {
    if (!GroovyPsiManager.isInheritorCached(qualifierType, CommonClassNames.JAVA_UTIL_COLLECTION)) return;

    final PsiType collectionType = PsiUtil.extractIterableTypeParameter(qualifierType, true);
    if (collectionType == null) return;

    final PsiScopeProcessor fieldSearcher = new FieldSearcher(processor, JavaPsiFacade.getInstance(place.getProject()).findClass(CommonClassNames.JAVA_UTIL_COLLECTION, place.getResolveScope()));
    ResolveUtil.processAllDeclarations(collectionType, fieldSearcher, state, place);
  }

  private static class FieldSearcher implements PsiScopeProcessor, ClassHint, ElementClassHint {
    final PsiClass collectionClass;
    final PsiScopeProcessor processor;

    public FieldSearcher(PsiScopeProcessor processor, PsiClass collectionClass) {
      this.collectionClass = collectionClass;
      this.processor = processor;
    }

    @Override
    public boolean execute(PsiElement element, ResolveState state) {
      if (element instanceof PsiField) {
        final PsiType type = ((PsiField)element).getType();
        final String typeText;
        if (type instanceof PsiClassType) {
          typeText = CommonClassNames.JAVA_UTIL_COLLECTION + "<" + type.getCanonicalText() + ">";
        }
        else {
          typeText = CommonClassNames.JAVA_UTIL_COLLECTION;
        }
        LightFieldBuilder lightField = new LightFieldBuilder(((PsiField)element).getName(), typeText, element).setContainingClass(
          collectionClass);
        return processor.execute(lightField, state);
      }
      return true;
    }

    @Override
    public <T> T getHint(Key<T> hintKey) {
      if (hintKey == NameHint.KEY) return processor.getHint(hintKey);
      if (hintKey == ClassHint.KEY || hintKey == ElementClassHint.KEY) return (T)this;
      return null;
    }

    @Override
    public void handleEvent(Event event, Object associated) {
      processor.handleEvent(event, associated);
    }

    @Override
    public boolean shouldProcess(ResolveKind resolveKind) {
      return resolveKind == ResolveKind.PROPERTY;
    }

    @Override
    public boolean shouldProcess(DeclarationKind kind) {
      return kind == DeclarationKind.FIELD;
    }
  }
}
