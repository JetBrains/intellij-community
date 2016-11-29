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
package org.jetbrains.plugins.groovy.actions.generate.equals;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.generation.*;
import com.intellij.codeInsight.generation.ui.GenerateEqualsWizard;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
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
import org.jetbrains.plugins.groovy.actions.generate.GroovyCodeInsightBundle;
import org.jetbrains.plugins.groovy.actions.generate.GroovyGenerationInfo;

import java.util.Collection;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 28.05.2008
 */
public class GroovyGenerateEqualsHandler extends GenerateMembersHandlerBase {

  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.actions.generate.equals.EqualsGenerateHandler");
  private PsiField[] myEqualsFields = null;
  private PsiField[] myHashCodeFields = null;
  private PsiField[] myNonNullFields = null;
  private static final PsiElementClassMember[] DUMMY_RESULT = new PsiElementClassMember[1];

  public GroovyGenerateEqualsHandler() {
    super("");
  }


  @Override
  @Nullable
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project) {
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
                    ? GroovyCodeInsightBundle.message("generate.equals.and.hashcode.already.defined.warning.anonymous")
                    : GroovyCodeInsightBundle.message("generate.equals.and.hashcode.already.defined.warning", aClass.getQualifiedName());

      if (Messages.showYesNoDialog(project, text,
                                   GroovyCodeInsightBundle.message("generate.equals.and.hashcode.already.defined.title"),
                                   Messages.getQuestionIcon()) == Messages.YES) {
        if (!ApplicationManager.getApplication().runWriteAction(new Computable<Boolean>() {
          @Override
          public Boolean compute() {
            try {
              equalsMethod.delete();
              hashCodeMethod.delete();
              return Boolean.TRUE;
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
              return Boolean.FALSE;
            }
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

  @Override
  public boolean startInWriteAction() {
      return true;
    } 
}
