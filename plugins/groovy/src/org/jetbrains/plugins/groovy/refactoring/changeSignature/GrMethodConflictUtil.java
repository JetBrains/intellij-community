/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.plugins.groovy.lang.documentation.GroovyPresentationUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GrMethodConflictUtil {
  private GrMethodConflictUtil() {
  }

  public static void checkMethodConflicts(PsiClass clazz,
                                          GrMethod prototype,
                                          GrMethod refactoredMethod,
                                          final MultiMap<PsiElement, String> conflicts, boolean excludeJavaConflicts) {
    List<MethodSignature> prototypeSignatures = GrClosureSignatureUtil.generateAllSignaturesForMethod(prototype, PsiSubstitutor.EMPTY);
    checkForClosurePropertySignatrureOverload(clazz, prototype, refactoredMethod, conflicts, prototypeSignatures);
    checkForMethodSignatureOverload(clazz, prototype, refactoredMethod, conflicts, excludeJavaConflicts, prototypeSignatures);

    checkForAccessorOverloading(clazz, prototype, conflicts);
  }

  private static void checkForClosurePropertySignatrureOverload(PsiClass clazz,
                                                                GrMethod prototype,
                                                                GrMethod refactoredMethod,
                                                                MultiMap<PsiElement, String> conflicts,
                                                                List<MethodSignature> prototypeSignatures) {
    final boolean isStatic = prototype.hasModifierProperty(GrModifier.STATIC);
    final String name = prototype.getName();
    if (!GroovyPropertyUtils.isProperty(clazz, name, isStatic)) return;

    final PsiMethod getter = GroovyPropertyUtils.findPropertyGetter(clazz, name, isStatic, true);

    final PsiType returnType;
    if (getter instanceof GrMethod) {
      returnType = ((GrMethod)getter).getInferredReturnType();
    }
    else if (getter instanceof GrAccessorMethod) {
      returnType = ((GrAccessorMethod)getter).getInferredReturnType();
    }
    else {
      return;
    }
    if (!(returnType instanceof GrClosureType)) return;

    final GrClosureSignature signature = ((GrClosureType)returnType).getSignature();

    NextSignature:
    for (MethodSignature prototypeSignature : prototypeSignatures) {
      final GrClosureParameter[] params = signature.getParameters();
      final PsiType[] types = prototypeSignature.getParameterTypes();
      if (types.length != params.length) continue;
      for (int i = 0; i < types.length; i++) {
        if (!TypesUtil.isAssignableByMethodCallConversion(types[i], params[i].getType(), refactoredMethod.getParameterList())) {
          continue NextSignature;
        }
      }
      conflicts.putValue(getter, GroovyRefactoringBundle.message("refactored.method.will.cover.closure.property", name, RefactoringUIUtil.getDescription(getter.getContainingClass(), false)));
    }
  }

  private static void checkForMethodSignatureOverload(PsiClass clazz,
                                                      GrMethod prototype,
                                                      GrMethod refactoredMethod,
                                                      MultiMap<PsiElement, String> conflicts,
                                                      boolean excludeJavaConflicts,
                                                      List<MethodSignature> prototypeSignatures) {
    if (excludeJavaConflicts) {
      prototypeSignatures.remove(prototype.getSignature(PsiSubstitutor.EMPTY));
    }

    String newName = prototype.getName();
    PsiMethod[] methods = clazz.findMethodsByName(newName, false);
    MultiMap<MethodSignature, PsiMethod> signatures = GrClosureSignatureUtil.findMethodSignatures(methods);
    for (MethodSignature prototypeSignature : prototypeSignatures) {
      for (PsiMethod method : signatures.get(prototypeSignature)) {
        if (method != refactoredMethod) {
          String signaturePresentation = GroovyPresentationUtil.getSignaturePresentation(prototypeSignature);
          conflicts.putValue(method, GroovyRefactoringBundle.message("method.duplicate", signaturePresentation,
                                                                     RefactoringUIUtil.getDescription(clazz, false)));
          break;
        }
      }
    }
  }

  private static void checkForAccessorOverloading(PsiClass clazz,
                                                  GrMethod prototype,
                                                  MultiMap<PsiElement, String> conflicts) {
    if (GroovyPropertyUtils.isSimplePropertySetter(prototype)) {
      String propertyName = GroovyPropertyUtils.getPropertyNameBySetter(prototype);
      PsiMethod setter =
        GroovyPropertyUtils.findPropertySetter(clazz, propertyName, prototype.hasModifierProperty(GrModifier.STATIC), false);
      if (setter instanceof GrAccessorMethod) {
        conflicts.putValue(setter, GroovyRefactoringBundle.message("replace.setter.for.property", propertyName));
      }
    }
    else if (GroovyPropertyUtils.isSimplePropertyGetter(prototype)) {
      boolean isStatic = prototype.hasModifierProperty(GrModifier.STATIC);
      String propertyName = GroovyPropertyUtils.getPropertyNameByGetter(prototype);
      PsiMethod getter = GroovyPropertyUtils.findPropertyGetter(clazz, propertyName, isStatic, false);
      if (getter instanceof GrAccessorMethod) {
        conflicts.putValue(getter, GroovyRefactoringBundle.message("replace.getter.for.property", propertyName));
      }
    }
  }
}
