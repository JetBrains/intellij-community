// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.builder;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;

import static com.intellij.psi.CommonClassNames.*;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE;
import static org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToUtilKt.DELEGATES_TO_TYPE_KEY;

public class XmlMarkupBuilderNonCodeMemberContributor extends BuilderMethodsContributor {

  private static final String FQN = "groovy.xml.MarkupBuilder";
  @NonNls private static final String ORIGIN_INFO = "via MarkupBuilder";

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
                                @NotNull Processor<? super PsiElement> processor) {
    GrLightMethodBuilder res;

    // ()
    res = createMethod(name, clazz, place);
    if (!processor.process(res)) return false;

    // (Closure)
    res = createMethod(name, clazz, place);
    res.addAndGetParameter("body", GROOVY_LANG_CLOSURE).putUserData(DELEGATES_TO_TYPE_KEY, FQN);
    if (!processor.process(res)) return false;

    // (Object, Closure)
    res = createMethod(name, clazz, place);
    res.addParameter("value", JAVA_LANG_OBJECT);
    res.addAndGetParameter("body", GROOVY_LANG_CLOSURE).putUserData(DELEGATES_TO_TYPE_KEY, FQN);
    if (!processor.process(res)) return false;

    // (Map, Closure)
    res = createMethod(name, clazz, place);
    res.addParameter("attributes", JAVA_UTIL_MAP);
    res.addAndGetParameter("body", GROOVY_LANG_CLOSURE).putUserData(DELEGATES_TO_TYPE_KEY, FQN);
    if (!processor.process(res)) return false;

    // (Map)
    // (Map, Object)
    // (Map, Object, Closure)
    res = createMethod(name, clazz, place);
    res.addParameter("attributes", JAVA_UTIL_MAP);
    res.addOptionalParameter("value", JAVA_LANG_OBJECT);
    res.addAndGetOptionalParameter("body", GROOVY_LANG_CLOSURE).putUserData(DELEGATES_TO_TYPE_KEY, FQN);
    for (GrReflectedMethod method : res.getReflectedMethods()) {
      if (!processor.process(method)) return false;
    }

    // (Object)
    // (Object, Map)
    // (Object, Map, Closure)
    res = createMethod(name, clazz, place);
    res.addParameter("value", JAVA_LANG_OBJECT);
    res.addOptionalParameter("attributes", JAVA_UTIL_MAP);
    res.addAndGetOptionalParameter("body", GROOVY_LANG_CLOSURE).putUserData(DELEGATES_TO_TYPE_KEY, FQN);
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
