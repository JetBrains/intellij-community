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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;

import static com.intellij.psi.CommonClassNames.*;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrDelegatesToUtil.DELEGATES_TO_KEY;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE;

public class XmlMarkupBuilderNonCodeMemberContributor extends BuilderMethodsContributor {

  private static final String FQN = "groovy.xml.MarkupBuilder";
  private static final String ORIGIN_INFO = "via MarkupBuilder";

  @Nullable
  @Override
  protected String getParentClassName() {
    return FQN;
  }

  @Override
  boolean processDynamicMethods(@NotNull PsiType qualifierType,
                                @NotNull PsiClass clazz,
                                @NotNull String name,
                                @NotNull PsiElement place,
                                @NotNull Processor<PsiElement> processor) {
    GrLightMethodBuilder res;

    // ()
    res = createMethod(name, clazz, place);
    if (!processor.process(res)) return false;

    // (Closure)
    res = createMethod(name, clazz, place);
    res.addAndGetParameter("body", GROOVY_LANG_CLOSURE, false).putUserData(DELEGATES_TO_KEY, FQN);
    if (!processor.process(res)) return false;

    // (Object, Closure)
    res = createMethod(name, clazz, place);
    res.addParameter("value", JAVA_LANG_OBJECT, false);
    res.addAndGetParameter("body", GROOVY_LANG_CLOSURE, false).putUserData(DELEGATES_TO_KEY, FQN);
    if (!processor.process(res)) return false;

    // (Map, Closure)
    res = createMethod(name, clazz, place);
    res.addParameter("attributes", JAVA_UTIL_MAP, false);
    res.addAndGetParameter("body", GROOVY_LANG_CLOSURE, false).putUserData(DELEGATES_TO_KEY, FQN);
    if (!processor.process(res)) return false;

    // (Map)
    // (Map, Object)
    // (Map, Object, Closure)
    res = createMethod(name, clazz, place);
    res.addParameter("attributes", JAVA_UTIL_MAP, false);
    res.addParameter("value", JAVA_LANG_OBJECT, true);
    res.addAndGetParameter("body", GROOVY_LANG_CLOSURE, true).putUserData(DELEGATES_TO_KEY, FQN);
    for (GrReflectedMethod method : res.getReflectedMethods()) {
      if (!processor.process(method)) return false;
    }

    // (Object)
    // (Object, Map)
    // (Object, Map, Closure)
    res = createMethod(name, clazz, place);
    res.addParameter("value", JAVA_LANG_OBJECT, false);
    res.addParameter("attributes", JAVA_UTIL_MAP, true);
    res.addAndGetParameter("body", GROOVY_LANG_CLOSURE, true).putUserData(DELEGATES_TO_KEY, FQN);
    for (GrReflectedMethod method : res.getReflectedMethods()) {
      if (!processor.process(method)) return false;
    }

    return true;
  }

  @NotNull
  private static GrLightMethodBuilder createMethod(@NotNull String name, @NotNull PsiClass clazz, @NotNull PsiElement place) {
    GrLightMethodBuilder res = new GrLightMethodBuilder(place.getManager(), name);
    res.setReturnType(JAVA_LANG_STRING, place.getResolveScope());
    res.setOriginInfo(ORIGIN_INFO);
    UtilsKt.setContainingClass(res, clazz);
    return res;
  }
}
