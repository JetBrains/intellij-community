// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  protected CreateFieldFix(PsiClass targetClass) {
    myTargetClass = targetClass;
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
