/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: 8/25/11
 */
public abstract class AbstractInplaceIntroduceTest<E extends PsiElement, V extends PsiNameIdentifierOwner> extends LightPlatformCodeInsightTestCase {
  @Nullable protected abstract V getLocalVariableFromEditor();
  @Nullable protected abstract E getExpressionFromEditor();

  protected abstract String getBasePath();
  protected abstract MyIntroduceHandler<E, V> createIntroduceHandler();

  protected void doTestEscape() throws Exception {
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    TemplateManagerImpl templateManager = (TemplateManagerImpl)TemplateManager.getInstance(LightPlatformTestCase.getProject());
    try {
      templateManager.setTemplateTesting(true);
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      final MyIntroduceHandler introduceFieldHandler = createIntroduceHandler();
      final E expression = getExpressionFromEditor();
      if (expression != null) {
        introduceFieldHandler.invokeImpl(LightPlatformTestCase.getProject(), expression, getEditor());
      } else {
        final V localVariable = getLocalVariableFromEditor();
        introduceFieldHandler.invokeImpl(LightPlatformTestCase.getProject(), localVariable, getEditor());
      }
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assert state != null;
      state.gotoEnd(true);
      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    }
    finally {
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
      templateManager.setTemplateTesting(false);
    }
  }

  protected abstract String getExtension();

  protected void doTest(final Pass<AbstractInplaceIntroducer> pass) throws Exception {
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    TemplateManagerImpl templateManager = (TemplateManagerImpl)TemplateManager.getInstance(LightPlatformTestCase.getProject());
    try {
      templateManager.setTemplateTesting(true);
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      final MyIntroduceHandler introduceFieldHandler = createIntroduceHandler();
      final E expression = getExpressionFromEditor();
      if (expression != null) {
        introduceFieldHandler.invokeImpl(LightPlatformTestCase.getProject(), expression, getEditor());
      } else {
        final V localVariable = getLocalVariableFromEditor();
        introduceFieldHandler.invokeImpl(LightPlatformTestCase.getProject(), localVariable, getEditor());
      }
      pass.pass(introduceFieldHandler.getInplaceIntroducer());
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assert state != null;
      state.gotoEnd(false);
      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    }
    finally {
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
      templateManager.setTemplateTesting(false);
    }
  }

  public interface MyIntroduceHandler<EH extends PsiElement, VH extends PsiNameIdentifierOwner> {
    boolean invokeImpl(Project project, @NotNull EH selectedExpr, Editor editor);
    boolean invokeImpl(Project project, VH localVariable, Editor editor);
    AbstractInplaceIntroducer getInplaceIntroducer();
  }
}
