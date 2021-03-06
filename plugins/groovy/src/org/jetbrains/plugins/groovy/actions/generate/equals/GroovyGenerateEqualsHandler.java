// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.actions.generate.equals;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.generation.*;
import com.intellij.codeInsight.generation.ui.GenerateEqualsWizard;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.actions.generate.GroovyGenerationInfo;

import java.util.Collection;
import java.util.List;

public class GroovyGenerateEqualsHandler extends GenerateMembersHandlerBase {
  private static final Logger LOG = Logger.getInstance(GroovyGenerateEqualsHandler.class);
  private static final PsiElementClassMember[] DUMMY_RESULT = new PsiElementClassMember[1];

  private PsiField[] myEqualsFields = null;
  private PsiField[] myHashCodeFields = null;
  private PsiField[] myNonNullFields = null;

  public GroovyGenerateEqualsHandler() {
    super("");
  }


  @Override
  protected ClassMember @Nullable [] chooseOriginalMembers(PsiClass aClass, Project project) {
    myEqualsFields = null;
    myHashCodeFields = null;
    myNonNullFields = PsiField.EMPTY_ARRAY;

    GlobalSearchScope scope = aClass.getResolveScope();
    final PsiMethod equalsMethod = GroovyGenerateEqualsHelper
      .findMethod(aClass, GroovyGenerateEqualsHelper.getEqualsSignature(project, scope));
    final PsiMethod hashCodeMethod = GroovyGenerateEqualsHelper.findMethod(aClass, GroovyGenerateEqualsHelper.getHashCodeSignature());

    boolean needEquals = equalsMethod == null;
    boolean needHashCode = hashCodeMethod == null;
    if (!needEquals && !needHashCode) {
      String text = aClass instanceof PsiAnonymousClass
                    ? GroovyBundle.message("generate.equals.and.hashcode.already.defined.warning.anonymous")
                    : GroovyBundle.message("generate.equals.and.hashcode.already.defined.warning", aClass.getQualifiedName());

      if (Messages.showYesNoDialog(project, text,
                                   GroovyBundle.message("generate.equals.and.hashcode.already.defined.title"),
                                   Messages.getQuestionIcon()) == Messages.YES) {
        if (!WriteAction.compute(() -> {
          try {
            equalsMethod.delete();
            hashCodeMethod.delete();
            return Boolean.TRUE;
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
            return Boolean.FALSE;
          }
        }).booleanValue()) {
          return null;
        }
        else {
          needEquals = needHashCode = true;
        }
      }
      else {
        return null;
      }
    }

    GenerateEqualsWizard wizard = new GenerateEqualsWizard(project, aClass, needEquals, needHashCode);
    if (!wizard.showAndGet()) {
      return null;
    }
    myEqualsFields = wizard.getEqualsFields();
    myHashCodeFields = wizard.getHashCodeFields();
    myNonNullFields = wizard.getNonNullFields();
    return DUMMY_RESULT;
  }

  @Override
  @NotNull
  protected List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] originalMembers) throws IncorrectOperationException {
    Project project = aClass.getProject();
    final boolean useInstanceofToCheckParameterType = CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER;

    GroovyGenerateEqualsHelper helper = new GroovyGenerateEqualsHelper(project, aClass, myEqualsFields, myHashCodeFields, myNonNullFields, useInstanceofToCheckParameterType);
    Collection<PsiMethod> methods = helper.generateMembers();
    return ContainerUtil.map2List(methods, (Function<PsiMethod, PsiGenerationInfo<PsiMethod>>)s -> new GroovyGenerationInfo<>(s));
  }

  @Override
  protected ClassMember[] getAllOriginalMembers(PsiClass aClass) {
    return ClassMember.EMPTY_ARRAY;
  }

  @Override
  protected GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember originalMember) throws IncorrectOperationException {
    return GenerationInfo.EMPTY_ARRAY;
  }

  @Override
  protected void cleanup() {
    super.cleanup();

    myEqualsFields = null;
    myHashCodeFields = null;
    myNonNullFields = null;
  }
}
