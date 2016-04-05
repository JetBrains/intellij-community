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
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyImportHelper;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.List;

/**
 * Created by Max Medvedev on 28/02/14
 */
public class FromStringHintProcessor extends SignatureHintProcessor {

  @Override
  public String getHintName() {
    return "groovy.transform.stc.FromString";
  }

  @NotNull
  @Override
  public List<PsiType[]> inferExpectedSignatures(@NotNull final PsiMethod method,
                                                 @NotNull final PsiSubstitutor substitutor,
                                                 @NotNull String[] options) {
    LightElement context = new FromStringLightElement(method);
    return ContainerUtil.map(options, new Function<String, PsiType[]>() {
      @Override
      public PsiType[] fun(String value) {
          String[] params = value.split(",");
          return ContainerUtil.map(params, new Function<String, PsiType>() {
            @Override
            public PsiType fun(String param) {
              try {
                PsiType original = JavaPsiFacade.getElementFactory(method.getProject()).createTypeFromText(param, context);
                return substitutor.substitute(original);
              }
              catch (IncorrectOperationException e) {
                //do nothing. Just don't throw an exception
              }
              return PsiType.NULL;
            }
          }, new PsiType[params.length]);
      }
    });
  }
}

class FromStringLightElement extends LightElement {

  private final PsiMethod myMethod;
  private final GroovyFile myFile;

  FromStringLightElement(@NotNull PsiMethod method) {
    super(method.getManager(), GroovyLanguage.INSTANCE);
    myMethod = method;
    myFile = GroovyPsiElementFactory.getInstance(getProject()).createGroovyFile("", false, null);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (!ResolveUtil.shouldProcessClasses(processor.getHint(ElementClassHint.KEY))) return true;

    for (PsiTypeParameter parameter : myMethod.getTypeParameters()) {
      if (!ResolveUtil.processElement(processor, parameter, state)) return false;
    }

    PsiClass containingClass = myMethod.getContainingClass();
    if (containingClass != null) {
    PsiTypeParameter[] parameters = containingClass.getTypeParameters();
      for (PsiTypeParameter parameter : parameters) {
        if (!ResolveUtil.processElement(processor, parameter, state)) return false;
      }
    }

    if (!GroovyImportHelper.processImplicitImports(processor, state, lastParent, place, myFile)) {
      return false;
    }

    // Suppose place is 'MyClass<T>' and MyClass belongs to default package.
    // This reference will not be resolved, because context has no parents (it has no parents at all.
    // See com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl.OurGenericsResolver.resolve()
    if (place instanceof PsiQualifiedReference) {
      PsiQualifiedReference reference = (PsiQualifiedReference)place;
      if (reference.getQualifier() == null && reference.getReferenceName() != null) {
        PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass(reference.getReferenceName(), getResolveScope());
        if (aClass != null && !ResolveUtil.processElement(processor, aClass, state)) {
          return false;
        }
      }
    }

    return true;
  }

  @Override
  public String toString() {
    return "fromStringLightElement";
  }
}
