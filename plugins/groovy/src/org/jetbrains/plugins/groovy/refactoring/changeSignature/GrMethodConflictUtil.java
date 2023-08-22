// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.plugins.groovy.lang.documentation.GroovyPresentationUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public final class GrMethodConflictUtil {
  private GrMethodConflictUtil() {
  }

  public static void checkMethodConflicts(PsiClass clazz,
                                          GrMethod prototype,
                                          GrMethod refactoredMethod,
                                          final MultiMap<PsiElement, String> conflicts, boolean excludeJavaConflicts) {
    List<MethodSignature> prototypeSignatures = GrClosureSignatureUtil.generateAllSignaturesForMethod(prototype, PsiSubstitutor.EMPTY);
    checkForClosurePropertySignatureOverload(clazz, prototype, refactoredMethod, conflicts, prototypeSignatures);
    checkForMethodSignatureOverload(clazz, prototype, refactoredMethod, conflicts, excludeJavaConflicts, prototypeSignatures);

    checkForAccessorOverloading(clazz, prototype, conflicts);
  }

  private static void checkForClosurePropertySignatureOverload(PsiClass clazz,
                                                               GrMethod prototype,
                                                               final GrMethod refactoredMethod,
                                                               final MultiMap<PsiElement, String> conflicts,
                                                               final List<MethodSignature> prototypeSignatures) {
    final boolean isStatic = prototype.hasModifierProperty(PsiModifier.STATIC);
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

    final List<GrSignature> signatures = ((GrClosureType)returnType).getSignatures();
    for (GrSignature signature : signatures) {
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
    MultiMap<MethodSignature, PsiMethod> signatures = GrClosureSignatureUtil.findRawMethodSignatures(methods, clazz);
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
        GroovyPropertyUtils.findPropertySetter(clazz, propertyName, prototype.hasModifierProperty(PsiModifier.STATIC), false);
      if (setter instanceof GrAccessorMethod) {
        conflicts.putValue(setter, GroovyRefactoringBundle.message("replace.setter.for.property", propertyName));
      }
    }
    else if (GroovyPropertyUtils.isSimplePropertyGetter(prototype)) {
      boolean isStatic = prototype.hasModifierProperty(PsiModifier.STATIC);
      String propertyName = GroovyPropertyUtils.getPropertyNameByGetter(prototype);
      PsiMethod getter = GroovyPropertyUtils.findPropertyGetter(clazz, propertyName, isStatic, false);
      if (getter instanceof GrAccessorMethod) {
        conflicts.putValue(getter, GroovyRefactoringBundle.message("replace.getter.for.property", propertyName));
      }
    }
  }
}
