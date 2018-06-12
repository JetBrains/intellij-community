// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.overrideImplement;

import com.intellij.codeInsight.MethodImplementor;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.actions.generate.GroovyGenerationInfo;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.jetbrains.plugins.groovy.util.GroovyOverrideImplementUtil;

/**
 * @author Medvedev Max
 */
public class GroovyMethodImplementor implements MethodImplementor {
  @NotNull
  @Override
  public PsiMethod[] getMethodsToImplement(PsiClass aClass) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiMethod[] createImplementationPrototypes(PsiClass inClass, PsiMethod method) throws IncorrectOperationException {
    if (!(inClass instanceof GrTypeDefinition)) return PsiMethod.EMPTY_ARRAY;
    if (method instanceof GrTraitMethod) return PsiMethod.EMPTY_ARRAY;

    final PsiClass containingClass = method.getContainingClass();
    PsiSubstitutor substitutor = inClass.isInheritor(containingClass, true)
                                 ? TypeConversionUtil.getSuperClassSubstitutor(containingClass, inClass, PsiSubstitutor.EMPTY)
                                 : PsiSubstitutor.EMPTY;
    return new PsiMethod[]{GroovyOverrideImplementUtil.generateMethodPrototype((GrTypeDefinition)inClass, method, substitutor)};
  }

  @Override
  public GenerationInfo createGenerationInfo(PsiMethod method, boolean mergeIfExists) {
    if (method instanceof GrMethod) {
      return new GroovyGenerationInfo<>((GrMethod)method, mergeIfExists);
    }
    return null;
  }

  @NotNull
  @Override
  public Consumer<PsiMethod> createDecorator(final PsiClass targetClass,
                                             final PsiMethod baseMethod,
                                             final boolean toCopyJavaDoc,
                                             final boolean insertOverrideIfPossible) {
    return new PsiMethodConsumer(targetClass, toCopyJavaDoc, baseMethod, insertOverrideIfPossible);
  }

  static class PsiMethodConsumer implements Consumer<PsiMethod> {
    private final PsiClass myTargetClass;
    private final boolean myToCopyJavaDoc;
    private final PsiMethod myBaseMethod;
    private final boolean myInsertOverrideIfPossible;

    public PsiMethodConsumer(PsiClass targetClass, boolean toCopyJavaDoc, PsiMethod baseMethod, boolean insertOverrideIfPossible) {
      myTargetClass = targetClass;
      myToCopyJavaDoc = toCopyJavaDoc;
      myBaseMethod = baseMethod;
      myInsertOverrideIfPossible = insertOverrideIfPossible;
    }

    @Override
    public void consume(PsiMethod method) {
      Project project = myTargetClass.getProject();

      if (myToCopyJavaDoc) {
        PsiDocComment baseMethodDocComment = myBaseMethod.getDocComment();
        if (baseMethodDocComment != null) {
          GrDocComment docComment =
            GroovyPsiElementFactory.getInstance(project).createDocCommentFromText(baseMethodDocComment.getText());
          GrDocCommentUtil.setDocComment(((GrMethod)method), docComment);
        }
      }
      else {
        PsiDocComment docComment = method.getDocComment();
        if (docComment != null) {
          docComment.delete();
        }
      }

      if (myInsertOverrideIfPossible) {
        if (OverrideImplementUtil.canInsertOverride(method, myTargetClass) &&
            JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OVERRIDE, myTargetClass.getResolveScope()) != null &&
            !method.getModifierList().hasAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE)) {
          method.getModifierList().addAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE);
        }
      }
      else {
        PsiAnnotation annotation = method.getModifierList().findAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE);
        if (annotation != null) {
          annotation.delete();
        }
      }
    }
  }
}
