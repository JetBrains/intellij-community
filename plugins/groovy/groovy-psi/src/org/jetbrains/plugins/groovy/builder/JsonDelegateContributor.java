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

import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrDelegatesToUtil.DELEGATES_TO_KEY;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE;

public class JsonDelegateContributor extends NonCodeMembersContributor {

  static final String DELEGATE_FQN = "groovy.json.JsonDelegate";

  @Nullable
  @Override
  protected String getParentClassName() {
    return DELEGATE_FQN;
  }

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiClass clazz,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    if (clazz == null) return;

    String name = ResolveUtil.getNameHint(processor);
    if (name == null) return;

    if (!ResolveUtil.shouldProcessMethods(processor.getHint(ElementClassHint.KEY))) return;

    JavaPsiFacade facade = JavaPsiFacade.getInstance(place.getProject());

    GrLightMethodBuilder method;
    PsiClassType genericType;

    // T (T)
    method = createMethod(name, clazz, place);
    genericType = facade.getElementFactory().createType(method.addTypeParameter("T"));
    method.addParameter("value", genericType, false);
    method.setReturnType(genericType);
    if (!processor.execute(method, state)) return;

    // List<T> (T[], Closure)
    method = createMethod(name, clazz, place);
    genericType = facade.getElementFactory().createType(method.addTypeParameter("T"));
    method.addParameter("values", genericType.createArrayType(), false);
    method.addAndGetParameter("c", GROOVY_LANG_CLOSURE, false).putUserData(DELEGATES_TO_KEY, DELEGATE_FQN);
    method.setReturnType(TypesUtil.createListType(place, genericType));
    if (!processor.execute(method, state)) return;

    // List<T> (Iterable<T>, Closure)
    method = createMethod(name, clazz, place);
    genericType = facade.getElementFactory().createType(method.addTypeParameter("T"));
    method.addParameter("values", TypesUtil.createIterableType(place, genericType), false);
    method.addAndGetParameter("c", GROOVY_LANG_CLOSURE, false).putUserData(DELEGATES_TO_KEY, DELEGATE_FQN);
    method.setReturnType(TypesUtil.createListType(place, genericType));
    if (!processor.execute(method, state)) return;

    // List<T> (T...)
    method = createMethod(name, clazz, place);
    genericType = facade.getElementFactory().createType(method.addTypeParameter("T"));
    method.addAndGetParameter("values", new PsiEllipsisType(genericType), false);
    method.setReturnType(TypesUtil.createListType(place, genericType));
    processor.execute(method, state);
  }

  private static GrLightMethodBuilder createMethod(@NotNull String name,
                                                   @NotNull PsiClass clazz,
                                                   @NotNull PsiElement context) {
    GrLightMethodBuilder method = new GrLightMethodBuilder(context.getManager(), name);
    method.setOriginInfo(JsonBuilderContributor.ORIGIN_INFO);
    method.addModifier(PsiModifier.PUBLIC);
    method.setContainingClass(clazz instanceof ClsClassImpl ? ((ClsClassImpl)clazz).getSourceMirrorClass() : clazz);
    return method;
  }
}
