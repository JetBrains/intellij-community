// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.builder;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP;
import static org.jetbrains.plugins.groovy.builder.JsonDelegateContributor.DELEGATE_FQN;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE;
import static org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToUtilKt.DELEGATES_TO_TYPE_KEY;

public class JsonBuilderContributor extends BuilderMethodsContributor {

  private static final String FQN = "groovy.json.JsonBuilder";
  @NonNls
  static final String ORIGIN_INFO = "via JsonBuilder";

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
    GrLightMethodBuilder method;

    // ()
    method = createMethod(name, clazz, place);
    if (!processor.process(method)) return false;

    // (Closure)
    method = createMethod(name, clazz, place);
    method.addAndGetParameter("c", GROOVY_LANG_CLOSURE).putUserData(DELEGATES_TO_TYPE_KEY, DELEGATE_FQN);
    if (!processor.process(method)) return false;

    // (Map)
    method = createMethod(name, clazz, place);
    method.addParameter("map", JAVA_UTIL_MAP);
    if (!processor.process(method)) return false;

    // (Map, Closure)
    method = createMethod(name, clazz, place);
    method.addParameter("map", JAVA_UTIL_MAP);
    method.addAndGetParameter("c", GROOVY_LANG_CLOSURE).putUserData(DELEGATES_TO_TYPE_KEY, DELEGATE_FQN);
    if (!processor.process(method)) return false;

    // (Iterable, Closure)
    method = createMethod(name, clazz, place);
    method.addParameter("value", TypesUtil.createIterableType(place, null));
    method.addAndGetParameter("c", GROOVY_LANG_CLOSURE).putUserData(DELEGATES_TO_TYPE_KEY, DELEGATE_FQN);
    if (!processor.process(method)) return false;

    // (Object[], Closure)
    method = createMethod(name, clazz, place);
    method.addParameter("value", TypesUtil.getJavaLangObject(place).createArrayType());
    method.addAndGetParameter("c", GROOVY_LANG_CLOSURE).putUserData(DELEGATES_TO_TYPE_KEY, DELEGATE_FQN);
    return processor.process(method);
  }


  private static GrLightMethodBuilder createMethod(@NotNull String name,
                                                   @NotNull PsiClass clazz,
                                                   @NotNull PsiElement context) {
    GrLightMethodBuilder method = new GrLightMethodBuilder(context.getManager(), name);
    method.setOriginInfo(ORIGIN_INFO);
    method.addModifier(PsiModifier.PUBLIC);
    method.setReturnType(JAVA_UTIL_MAP, context.getResolveScope());
    UtilsKt.setContainingClass(method, clazz);
    return method;
  }
}
