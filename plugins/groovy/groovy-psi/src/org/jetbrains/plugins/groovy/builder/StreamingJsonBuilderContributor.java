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
import com.intellij.psi.PsiType;
import com.intellij.util.Processor;
import groovy.lang.Closure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrDelegatesToUtil.DELEGATES_TO_KEY;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrDelegatesToUtil.DELEGATES_TO_STRATEGY_KEY;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE;

public class StreamingJsonBuilderContributor extends BuilderMethodsContributor {

  static final String ORIGIN_INFO = "via StreamingJsonBuilder";

  @Nullable
  @Override
  protected String getParentClassName() {
    return "groovy.json.StreamingJsonBuilder";
  }

  @NotNull
  protected String getDelegateClassName() {
    return "groovy.json.StreamingJsonBuilder.StreamingJsonDelegate";
  }

  @Override
  boolean processDynamicMethods(@NotNull PsiType qualifierType,
                                @NotNull PsiClass clazz,
                                @NotNull String name,
                                @NotNull PsiElement place,
                                @NotNull Processor<PsiElement> processor) {
    GrLightMethodBuilder method;

    // ()
    method = createMethod(name, place, qualifierType, clazz);
    if (!processor.process(method)) return false;

    // (Closure)
    method = createMethod(name, place, qualifierType, clazz);
    addClosureParameter(method);
    if (!processor.process(method)) return false;

    // (Map)
    method = createMethod(name, place, qualifierType, clazz);
    method.addParameter("args", JAVA_UTIL_MAP);
    if (!processor.process(method)) return false;

    // (Map, Closure)
    method = createMethod(name, place, qualifierType, clazz);
    method.addParameter("args", JAVA_UTIL_MAP);
    addClosureParameter(method);
    if (!processor.process(method)) return false;

    // (Iterable, Closure)
    method = createMethod(name, place, qualifierType, clazz);
    method.addParameter("values", TypesUtil.createIterableType(place, null), false);
    addClosureParameter(method);
    if (!processor.process(method)) return false;

    // (Object[], Closure)
    method = createMethod(name, place, qualifierType, clazz);
    method.addParameter("values", TypesUtil.getJavaLangObject(place).createArrayType());
    addClosureParameter(method);
    return processor.process(method);
  }

  @NotNull
  private static GrLightMethodBuilder createMethod(@NotNull String name,
                                                   @NotNull PsiElement place,
                                                   @NotNull PsiType returnType,
                                                   @NotNull PsiClass clazz) {
    GrLightMethodBuilder method = new GrLightMethodBuilder(place.getManager(), name);
    method.setModifiers(GrModifierFlags.PUBLIC_MASK);
    method.setReturnType(returnType);
    UtilsKt.setContainingClass(method, clazz);
    method.setOriginInfo(ORIGIN_INFO);
    return method;
  }

  protected void addClosureParameter(GrLightMethodBuilder method) {
    GrLightParameter closureParam = method.addAndGetParameter("closure", GROOVY_LANG_CLOSURE);
    closureParam.putUserData(DELEGATES_TO_KEY, getDelegateClassName());
    closureParam.putUserData(DELEGATES_TO_STRATEGY_KEY, Closure.OWNER_FIRST);
  }
}
