// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.gant.ant;

import com.intellij.lang.ant.AntIntrospector;
import com.intellij.lang.ant.dom.AntDomExtender;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.scope.PsiScopeProcessor;
import icons.AntIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class AntBuilderMethod extends LightMethodBuilder {
  private final @NotNull PsiFile myFile;
  private final @Nullable Class<?> myAntClass;

  AntBuilderMethod(@NotNull PsiFile file, @NotNull String name, @Nullable Class<?> antClass) {
    super(file.getManager(), GroovyLanguage.INSTANCE, name);
    myFile = file;
    myAntClass = antClass;
    setModifiers(PsiModifier.PUBLIC);
    setBaseIcon(AntIcons.AntTask);
    setMethodReturnType(() -> PsiType.getJavaLangObject(getManager(), getResolveScope()));
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    if (myAntClass != null) {
      final PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(myAntClass.getName(), myFile.getResolveScope());
      if (psiClass != null) {
        return psiClass;
      }
    }
    return this;
  }

  public boolean processNestedElements(PsiScopeProcessor processor) {
    if (!ResolveUtilKt.shouldProcessMethods(processor)) return true;
    final AntIntrospector introspector = AntDomExtender.getIntrospector(myAntClass);
    if (introspector == null) {
      return true;
    }

    final String expectedName = ResolveUtil.getNameHint(processor);
    final PsiType mapType = TypesUtil.createType(GroovyCommonClassNames.JAVA_UTIL_LINKED_HASH_MAP, myFile);
    final PsiType stringType = TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, myFile);
    final PsiType closureType = TypesUtil.createType(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, myFile);

    for (String name : Collections.list(introspector.getNestedElements())) {
      if (expectedName != null && !expectedName.equals(name)) {
        continue;
      }
      Class<?> antClass = introspector.getElementType(name);
      for (LightMethodBuilder method : methods(myFile, name, antClass, mapType, stringType, closureType)) {
        if (!processor.execute(method, ResolveState.initial())) {
          return false;
        }
      }
    }
    return true;
  }

  @NotNull
  static List<LightMethodBuilder> methods(@NotNull PsiFile file,
                                          @NotNull String name,
                                          @Nullable Class<?> antClass,
                                          @NotNull PsiType mapType,
                                          @NotNull PsiType stringType,
                                          @NotNull PsiType closureType) {
    // (Map, String, Closure)
    // (Map, String)
    AntBuilderMethod method = new AntBuilderMethod(file, name, antClass);
    method.addParameter(new GrLightParameter("args", mapType, method));
    method.addParameter(new GrLightParameter("singleArg", stringType, method));
    method.addParameter(new GrLightParameter("body", closureType, method).setOptional(true));

    // (Map, Closure)
    // (Map)
    AntBuilderMethod method2 = new AntBuilderMethod(file, name, antClass);
    method2.addParameter(new GrLightParameter("args", mapType, method2));
    method2.addParameter(new GrLightParameter("body", closureType, method2).setOptional(true));


    // (String, Closure)
    // (String)
    AntBuilderMethod method3 = new AntBuilderMethod(file, name, antClass);
    method3.addParameter(new GrLightParameter("singleArg", stringType, method3));
    method3.addParameter(new GrLightParameter("body", closureType, method3).setOptional(true));

    // (Closure)
    // ()
    AntBuilderMethod method4 = new AntBuilderMethod(file, name, antClass);
    method4.addParameter(new GrLightParameter("body", closureType, method4).setOptional(true));

    return Arrays.asList(method, method2, method3, method4);
  }
}
