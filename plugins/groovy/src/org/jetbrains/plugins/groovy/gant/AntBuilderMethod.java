// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.gant;

import com.intellij.lang.ant.AntIntrospector;
import com.intellij.lang.ant.dom.AntDomExtender;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.scope.PsiScopeProcessor;
import icons.AntIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrBuilderMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt;

import java.util.Collections;

/**
* @author peter
*/
class AntBuilderMethod extends LightMethodBuilder implements GrBuilderMethod {
  private final PsiFile myPlace;
  @Nullable private final Class myAntClass;

  AntBuilderMethod(PsiFile place, String name, PsiType closureType, @Nullable Class antClass, final PsiType stringType) {
    super(place.getManager(), GroovyLanguage.INSTANCE, name);
    myPlace = place;
    myAntClass = antClass;
    setModifiers(PsiModifier.PUBLIC);
    addParameter("args", GroovyCommonClassNames.JAVA_UTIL_LINKED_HASH_MAP);
    setBaseIcon(AntIcons.Task);
    addParameter(new GrLightParameter("singleArg", stringType, this).setOptional(true));
    addParameter(new GrLightParameter("body", closureType, this).setOptional(true));
    setMethodReturnType(() -> PsiType.getJavaLangObject(getManager(), getResolveScope()));
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    if (myAntClass != null) {
      final PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(myAntClass.getName(), myPlace.getResolveScope());
      if (psiClass != null) {
        return psiClass;
      }
    }
    return this;
  }

  public boolean processNestedElements(PsiScopeProcessor processor) {
    if (!ResolveUtilKt.shouldProcessMethods(processor)) return true;
    final AntIntrospector introspector = AntDomExtender.getIntrospector(myAntClass);
    if (introspector != null) {
      String expectedName = ResolveUtil.getNameHint(processor);
      final PsiType stringType = getParameterList().getParameters()[1].getType();
      final PsiType closureType = getParameterList().getParameters()[2].getType();

      for (String name : Collections.list(introspector.getNestedElements())) {
        if (expectedName == null || expectedName.equals(name)) {
          final AntBuilderMethod method = new AntBuilderMethod(myPlace, name, closureType, introspector.getElementType(name),
                                                               stringType);
          if (!processor.execute(method, ResolveState.initial())) return false;
        }
      }
    }
    return true;
  }

  @Override
  public boolean hasObligatoryNamedArguments() {
    return false;
  }
}
