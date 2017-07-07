/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE;
import static org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToUtilKt.DELEGATES_TO_KEY;

public class JsonDelegateContributor extends BuilderMethodsContributor {

  static final String DELEGATE_FQN = "groovy.json.JsonDelegate";

  @Nullable
  @Override
  protected String getParentClassName() {
    return DELEGATE_FQN;
  }

  @Override
  boolean processDynamicMethods(@NotNull PsiType qualifierType,
                                @NotNull PsiClass clazz,
                                @NotNull String name,
                                @NotNull PsiElement place,
                                @NotNull Processor<PsiElement> processor) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(place.getProject());

    GrLightMethodBuilder method;
    PsiClassType genericType;

    // List ()
    method = createMethod(name, clazz, place);
    method.setReturnType(TypesUtil.createListType(place, null));
    if (!processor.process(method)) return false;

    // T (T)
    method = createMethod(name, clazz, place);
    genericType = facade.getElementFactory().createType(method.addTypeParameter("T"));
    method.addParameter("value", genericType, false);
    method.setReturnType(genericType);
    if (!processor.process(method)) return false;

    // List<T> (T[], Closure)
    method = createMethod(name, clazz, place);
    genericType = facade.getElementFactory().createType(method.addTypeParameter("T"));
    method.addParameter("values", genericType.createArrayType(), false);
    method.addAndGetParameter("c", GROOVY_LANG_CLOSURE, false).putUserData(DELEGATES_TO_KEY, DELEGATE_FQN);
    method.setReturnType(TypesUtil.createListType(place, genericType));
    if (!processor.process(method)) return false;

    // List<T> (Iterable<T>, Closure)
    method = createMethod(name, clazz, place);
    genericType = facade.getElementFactory().createType(method.addTypeParameter("T"));
    method.addParameter("values", TypesUtil.createIterableType(place, genericType), false);
    method.addAndGetParameter("c", GROOVY_LANG_CLOSURE, false).putUserData(DELEGATES_TO_KEY, DELEGATE_FQN);
    method.setReturnType(TypesUtil.createListType(place, genericType));
    if (!processor.process(method)) return false;

    // List<T> (T...)
    method = createMethod(name, clazz, place);
    genericType = facade.getElementFactory().createType(method.addTypeParameter("T"));
    method.addAndGetParameter("values", new PsiEllipsisType(genericType), false);
    method.setReturnType(TypesUtil.createListType(place, genericType));
    return processor.process(method);
  }

  private static GrLightMethodBuilder createMethod(@NotNull String name,
                                                   @NotNull PsiClass clazz,
                                                   @NotNull PsiElement context) {
    GrLightMethodBuilder method = new GrLightMethodBuilder(context.getManager(), name);
    method.setOriginInfo(JsonBuilderContributor.ORIGIN_INFO);
    method.addModifier(PsiModifier.PUBLIC);
    UtilsKt.setContainingClass(method, clazz);
    return method;
  }
}
