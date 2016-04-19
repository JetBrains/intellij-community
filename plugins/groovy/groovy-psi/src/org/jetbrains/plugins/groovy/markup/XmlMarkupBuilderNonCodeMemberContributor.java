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
package org.jetbrains.plugins.groovy.markup;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import static com.intellij.psi.CommonClassNames.*;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrDelegatesToUtil.DELEGATES_TO_KEY;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE;

public class XmlMarkupBuilderNonCodeMemberContributor extends NonCodeMembersContributor {

  private static final String FQN = "groovy.xml.MarkupBuilder";

  @Nullable
  @Override
  protected String getParentClassName() {
    return FQN;
  }

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiClass aClass,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    String name = ResolveUtil.getNameHint(processor);
    if (name == null) return;

    if (!ResolveUtil.shouldProcessMethods(processor.getHint(ElementClassHint.KEY))) return;

    GrLightMethodBuilder res;

    // ()
    res = new GrLightMethodBuilder(aClass.getManager(), name);
    res.setReturnType(JAVA_LANG_STRING, place.getResolveScope());
    res.setOriginInfo("via MarkupBuilder");
    if (!processor.execute(res, state)) return;

    // (Closure)
    res = new GrLightMethodBuilder(aClass.getManager(), name);
    res.addAndGetParameter("body", GROOVY_LANG_CLOSURE, false).putUserData(DELEGATES_TO_KEY, FQN);
    res.setReturnType(JAVA_LANG_STRING, place.getResolveScope());
    res.setOriginInfo("via MarkupBuilder");
    if (!processor.execute(res, state)) return;

    // (Object, Closure)
    res = new GrLightMethodBuilder(aClass.getManager(), name);
    res.addParameter("value", JAVA_LANG_OBJECT, false);
    res.addAndGetParameter("body", GROOVY_LANG_CLOSURE, false).putUserData(DELEGATES_TO_KEY, FQN);
    res.setReturnType(JAVA_LANG_STRING, place.getResolveScope());
    res.setOriginInfo("via MarkupBuilder");
    if (!processor.execute(res, state)) return;

    // (Map, Closure)
    res = new GrLightMethodBuilder(aClass.getManager(), name);
    res.addParameter("attributes", JAVA_UTIL_MAP, false);
    res.addAndGetParameter("body", GROOVY_LANG_CLOSURE, false).putUserData(DELEGATES_TO_KEY, FQN);
    res.setReturnType(JAVA_LANG_STRING, place.getResolveScope());
    res.setOriginInfo("via MarkupBuilder");
    if (!processor.execute(res, state)) return;

    // (Map)
    // (Map, Object)
    // (Map, Object, Closure)
    res = new GrLightMethodBuilder(aClass.getManager(), name);
    res.setReturnType(JAVA_LANG_STRING, place.getResolveScope());
    res.addParameter("attributes", JAVA_UTIL_MAP, false);
    res.addParameter("value", JAVA_LANG_OBJECT, true);
    res.addAndGetParameter("body", GROOVY_LANG_CLOSURE, true).putUserData(DELEGATES_TO_KEY, FQN);
    res.setOriginInfo("via MarkupBuilder");
    for (GrReflectedMethod method : res.getReflectedMethods()) {
      if (!processor.execute(method, state)) return;
    }

    // (Object)
    // (Object, Map)
    // (Object, Map, Closure)
    res = new GrLightMethodBuilder(aClass.getManager(), name);
    res.setReturnType(JAVA_LANG_STRING, place.getResolveScope());
    res.addParameter("value", JAVA_LANG_OBJECT, false);
    res.addParameter("attributes", JAVA_UTIL_MAP, true);
    res.addAndGetParameter("body", GROOVY_LANG_CLOSURE, true).putUserData(DELEGATES_TO_KEY, FQN);
    res.setOriginInfo("via MarkupBuilder");
    for (GrReflectedMethod method : res.getReflectedMethods()) {
      if (!processor.execute(method, state)) return;
    }
  }
}
