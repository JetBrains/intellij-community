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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFieldFromUsageHelper;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author Maxim.Medvedev
 */
public class CreateFieldFix {
  private final PsiClass myTargetClass;

  protected PsiClass getTargetClass() {
    return myTargetClass;
  }

  protected CreateFieldFix(PsiClass targetClass) {
    myTargetClass = targetClass;
  }

  public boolean isAvailable() {
    return myTargetClass.isValid();
  }

  protected void doFix(@NotNull Project project,
                       @NotNull @GrModifier.ModifierConstant String[] modifiers,
                       @NotNull @NonNls String fieldName,
                       @NotNull TypeConstraint[] typeConstraints,
                       @NotNull PsiElement context) throws IncorrectOperationException {
    JVMElementFactory factory = JVMElementFactories.getFactory(myTargetClass.getLanguage(), project);
    if (factory == null) return;

    PsiField field = factory.createField(fieldName, PsiType.INT);
    if (myTargetClass instanceof GroovyScriptClass) {
      field.getModifierList().addAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_FIELD);
    }

    for (@GrModifier.ModifierConstant String modifier : modifiers) {
      PsiUtil.setModifierProperty(field, modifier, true);
    }

    field = CreateFieldFromUsageHelper.insertField(myTargetClass, field, context);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(field.getParent());

    Editor newEditor = IntentionUtils.positionCursor(project, myTargetClass.getContainingFile(), field);

    Template template = CreateFieldFromUsageHelper.setupTemplate(field, typeConstraints, myTargetClass, newEditor, context, false);
    TemplateManager manager = TemplateManager.getInstance(project);
    manager.startTemplate(newEditor, template);
  }
}
