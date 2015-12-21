/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.GrDelegatingScopeProcessorWithHints;

import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.RESOLVE_KINDS_PROPERTY;

/**
 * @author Maxim.Medvedev
 */
public class GrCollectionTypeMembersProvider extends NonCodeMembersContributor {

  @Override
  public String getParentClassName() {
    return CommonClassNames.JAVA_UTIL_COLLECTION;
  }

  @Override
  public void processDynamicElements(@NotNull final PsiType qualifierType,
                                     PsiClass aClass,
                                     @NotNull final PsiScopeProcessor processor,
                                     @NotNull final PsiElement place,
                                     @NotNull final ResolveState state) {
    final PsiType collectionType = PsiUtil.extractIterableTypeParameter(qualifierType, true);
    if (collectionType == null) return;

    PsiClass collectionClass = JavaPsiFacade.getInstance(place.getProject()).findClass(CommonClassNames.JAVA_UTIL_COLLECTION, place.getResolveScope());
    final PsiScopeProcessor fieldSearcher = new FieldSearcher(processor, collectionClass);
    ResolveUtil.processAllDeclarations(collectionType, fieldSearcher, state, place);
  }

  private static class FieldSearcher extends GrDelegatingScopeProcessorWithHints {
    final PsiClass myCollectionClass;

    public FieldSearcher(PsiScopeProcessor processor, PsiClass collectionClass) {
      super(processor, null, RESOLVE_KINDS_PROPERTY);
      myCollectionClass = collectionClass;
    }

    @Override
    public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
      if (element instanceof PsiField) {
        final PsiType type = ((PsiField)element).getType();
        final String typeText = type instanceof PsiClassType ? CommonClassNames.JAVA_UTIL_COLLECTION + "<" + type.getCanonicalText() + ">"
                                                             : CommonClassNames.JAVA_UTIL_COLLECTION;
        LightFieldBuilder lightField = new LightFieldBuilder(((PsiField)element).getName(), typeText, element);
        lightField.setContainingClass(myCollectionClass);
        lightField.setOriginInfo("spread collection field");
        return super.execute(lightField, state);
      }
      return true;
    }
  }
}
