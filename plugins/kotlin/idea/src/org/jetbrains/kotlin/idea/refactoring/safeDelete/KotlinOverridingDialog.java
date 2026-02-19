// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.safeDelete;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementDescriptionUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.safeDelete.OverridingMethodsDialog;
import com.intellij.refactoring.util.RefactoringDescriptionLocation;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle;

import java.util.List;

class KotlinOverridingDialog extends OverridingMethodsDialog {
    KotlinOverridingDialog(Project project, List<? extends UsageInfo> overridingMethods) {
        super(project, overridingMethods);
    }

    @Override
    protected @NlsContexts.DialogTitle @NotNull String getTitleText() {
        return KotlinBundle.message("override.declaration.unused.overriding.methods.title");
    }

    @Nls
    @Override
    protected @NotNull String getDescriptionText() {
        return KotlinBundle.message("override.declaration.choose.to.delete");
    }

    @Override
    protected @NotNull String getColumnName() {
        return KotlinBundle.message("override.declaration.member");
    }

    @Override
    protected String getElementDescription(UsageInfo info) {
        PsiElement overridingElement = ((KotlinSafeDeleteOverridingUsageInfo) info).getOverridingElement();
        String description = ElementDescriptionUtil.getElementDescription(overridingElement, RefactoringDescriptionLocation.WITH_PARENT);
        return HtmlChunk.html().addRaw(StringUtil.capitalize(description)).toString();
    }
}

