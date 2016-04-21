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

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP;
import static org.jetbrains.plugins.groovy.builder.JsonDelegateContributor.DELEGATE_FQN;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrDelegatesToUtil.DELEGATES_TO_KEY;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE;

public class JsonBuilderContributor extends NonCodeMembersContributor {

  private static final String FQN = "groovy.json.JsonBuilder";
  static final String ORIGIN_INFO = "via JsonBuilder";

  @Nullable
  @Override
  protected String getParentClassName() {
    return FQN;
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

    GrLightMethodBuilder method;

    // ()
    method = createMethod(name, clazz, place);
    if (!processor.execute(method, state)) return;

    // (Closure)
    method = createMethod(name, clazz, place);
    method.addAndGetParameter("c", GROOVY_LANG_CLOSURE, false).putUserData(DELEGATES_TO_KEY, DELEGATE_FQN);
    if (!processor.execute(method, state)) return;

    // (Map)
    method = createMethod(name, clazz, place);
    method.addParameter("map", JAVA_UTIL_MAP, false);
    if (!processor.execute(method, state)) return;

    // (Map, Closure)
    method = createMethod(name, clazz, place);
    method.addParameter("map", JAVA_UTIL_MAP, false);
    method.addAndGetParameter("c", GROOVY_LANG_CLOSURE, false).putUserData(DELEGATES_TO_KEY, DELEGATE_FQN);
    if (!processor.execute(method, state)) return;

    // (Iterable, Closure)
    method = createMethod(name, clazz, place);
    method.addParameter("value", TypesUtil.createIterableType(place, null), false);
    method.addAndGetParameter("c", GROOVY_LANG_CLOSURE, false).putUserData(DELEGATES_TO_KEY, DELEGATE_FQN);
    if (!processor.execute(method, state)) return;

    // (Object[], Closure)
    method = createMethod(name, clazz, place);
    method.addParameter("value", TypesUtil.getJavaLangObject(place).createArrayType(), false);
    method.addAndGetParameter("c", GROOVY_LANG_CLOSURE, false).putUserData(DELEGATES_TO_KEY, DELEGATE_FQN);
    processor.execute(method, state);
  }


  private static GrLightMethodBuilder createMethod(@NotNull String name,
                                                   @NotNull PsiClass clazz,
                                                   @NotNull PsiElement context) {
    GrLightMethodBuilder method = new GrLightMethodBuilder(context.getManager(), name);
    method.setOriginInfo(ORIGIN_INFO);
    method.addModifier(PsiModifier.PUBLIC);
    method.setReturnType(JAVA_UTIL_MAP, context.getResolveScope());
    method.setContainingClass(clazz instanceof ClsClassImpl ? ((ClsClassImpl)clazz).getSourceMirrorClass() : clazz);
    return method;
  }
}
