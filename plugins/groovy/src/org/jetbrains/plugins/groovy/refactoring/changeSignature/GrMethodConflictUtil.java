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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.MethodSignature;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.plugins.groovy.lang.documentation.GroovyPresentationUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
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
                                          final MultiMap<PsiElement, String> conflicts) {
    checkForSignatureOverload(clazz, prototype, refactoredMethod, conflicts);
    checkForAccessorOverloading(clazz, prototype, conflicts);
  }

  private static void checkForSignatureOverload(PsiClass clazz,
                                                GrMethod prototype,
                                                GrMethod refactoredMethod,
                                                MultiMap<PsiElement, String> conflicts) {
    String newName = prototype.getName();
    PsiMethod[] methods = clazz.findMethodsByName(newName, false);
    MultiMap<MethodSignature, PsiMethod> signatures = GrClosureSignatureUtil.findMethodSignatures(methods);
    List<MethodSignature> prototypeSignatures = GrClosureSignatureUtil.generateAllSignaturesForMethod(prototype, PsiSubstitutor.EMPTY);

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
        GroovyPropertyUtils.findPropertySetter(clazz, propertyName, prototype.hasModifierProperty(
          GrModifier.STATIC), false);
      if (setter instanceof GrAccessorMethod) {
        conflicts.putValue(setter, GroovyRefactoringBundle.message("replace.setter.for.property", propertyName));
      }
    }
    else if (GroovyPropertyUtils.isSimplePropertyGetter(prototype)) {
      boolean isStatic = prototype.hasModifierProperty(
        GrModifier.STATIC);
      String propertyName = GroovyPropertyUtils.getPropertyNameByGetter(prototype);
      PsiMethod getter =
        GroovyPropertyUtils.findPropertyGetter(clazz, propertyName, isStatic, false);
      if (getter instanceof GrAccessorMethod) {
        conflicts.putValue(getter, GroovyRefactoringBundle.message("replace.getter.for.property", propertyName));
      }
    }
  }
}
