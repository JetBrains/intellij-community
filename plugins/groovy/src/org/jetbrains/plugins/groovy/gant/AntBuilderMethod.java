/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.gant;

import com.intellij.lang.ant.AntIntrospector;
import com.intellij.lang.ant.dom.AntDomExtender;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.scope.PsiScopeProcessor;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrBuilderMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Collections;

/**
* @author peter
*/
class AntBuilderMethod extends LightMethodBuilder implements GrBuilderMethod {
  private final PsiFile myPlace;
  @Nullable private final Class myAntClass;

  public AntBuilderMethod(PsiFile place, String name, PsiType closureType, @Nullable Class antClass, final PsiType stringType) {
    super(place.getManager(), GroovyLanguage.INSTANCE, name);
    myPlace = place;
    myAntClass = antClass;
    setModifiers(PsiModifier.PUBLIC);
    addParameter("args", GrMapType.create(place.getResolveScope()));
    setBaseIcon(JetgroovyIcons.Groovy.Ant_task);
    addParameter(new GrLightParameter("singleArg", stringType, this).setOptional(true));
    addParameter(new GrLightParameter("body", closureType, this).setOptional(true));
    setMethodReturnType(new Computable<PsiType>() {
      @Override
      public PsiType compute() {
        return PsiType.getJavaLangObject(getManager(), getResolveScope());
      }
    });
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
