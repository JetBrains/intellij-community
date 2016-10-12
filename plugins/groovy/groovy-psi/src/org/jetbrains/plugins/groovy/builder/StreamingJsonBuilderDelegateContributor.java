/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.builder;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiType;
import com.intellij.util.Processor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;

public class StreamingJsonBuilderDelegateContributor extends StreamingJsonBuilderContributor {

  @Nullable
  @Override
  protected String getParentClassName() {
    return getDelegateClassName();
  }

  @Override
  boolean processDynamicMethods(@NotNull PsiType qualifierType,
                                @NotNull PsiClass clazz,
                                @NotNull String name,
                                @NotNull PsiElement place,
                                @NotNull Processor<PsiElement> processor) {
    GrLightMethodBuilder method;

    // (Object...)
    method = createMethod(name, place, clazz);
    method.addParameter("values", new PsiEllipsisType(TypeUtils.getObjectType(place)));
    if (!processor.process(method)) return false;

    // (Closure)
    method = createMethod(name, place, clazz);
    addClosureParameter(method);
    if (!processor.process(method)) return false;

    // (Iterable, Closure)
    method = createMethod(name, place, clazz);
    method.addParameter("values", TypesUtil.createIterableType(place, null));
    addClosureParameter(method);
    if (!processor.process(method)) return false;

    // (Object[], Closure)
    method = createMethod(name, place, clazz);
    method.addParameter("values", TypesUtil.getJavaLangObject(place).createArrayType());
    addClosureParameter(method);
    if (!processor.process(method)) return false;

    // (Object, Closure)
    method = createMethod(name, place, clazz);
    method.addParameter("value", TypesUtil.getJavaLangObject(place));
    addClosureParameter(method);
    return processor.process(method);
  }

  @NotNull
  private static GrLightMethodBuilder createMethod(@NotNull String name,
                                                   @NotNull PsiElement place,
                                                   @NotNull PsiClass clazz) {
    GrLightMethodBuilder method = new GrLightMethodBuilder(place.getManager(), name);
    method.setModifiers(GrModifierFlags.PUBLIC_MASK);
    method.setReturnType(TypesUtil.getJavaLangObject(place));
    UtilsKt.setContainingClass(method, clazz);
    method.setOriginInfo(StreamingJsonBuilderContributor.ORIGIN_INFO);
    return method;
  }
}
