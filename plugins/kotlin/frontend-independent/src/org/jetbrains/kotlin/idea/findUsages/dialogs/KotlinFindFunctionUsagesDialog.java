// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.findUsages.dialogs;

import com.intellij.find.FindSettings;
import com.intellij.find.findUsages.FindMethodUsagesDialog;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.JavaMethodFindUsagesOptions;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.StateRestoringCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.asJava.elements.KtLightMethod;
import org.jetbrains.kotlin.idea.KotlinBundle;
import org.jetbrains.kotlin.idea.findUsages.KotlinFunctionFindUsagesOptions;
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtNamedDeclaration;
import org.jetbrains.kotlin.psi.psiUtil.PsiUtilsKt;

import javax.swing.*;

public class KotlinFindFunctionUsagesDialog extends FindMethodUsagesDialog {
    private StateRestoringCheckBox expectedUsages;

    public KotlinFindFunctionUsagesDialog(
            PsiMethod method,
            Project project,
            KotlinFunctionFindUsagesOptions findUsagesOptions,
            boolean toShowInNewTab,
            boolean mustOpenInNewTab,
            boolean isSingleFile,
            FindUsagesHandler handler
    ) {
        super(method, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
    }

    @NotNull
    @Override
    protected KotlinFunctionFindUsagesOptions getFindUsagesOptions() {
        return (KotlinFunctionFindUsagesOptions) myFindUsagesOptions;
    }

    @Override
    public void configureLabelComponent(@NotNull SimpleColoredComponent coloredComponent) {
        coloredComponent.append(KotlinSearchUsagesSupport.Companion.formatJavaOrLightMethod((PsiMethod) myPsiElement));
    }

    @Override
    protected JPanel createFindWhatPanel() {
        JPanel findWhatPanel = super.createFindWhatPanel();

        if (findWhatPanel != null) {
            Utils.renameCheckbox(
                    findWhatPanel,
                    JavaBundle.message("find.what.implementing.methods.checkbox"),
                    KotlinBundle.message("find.declaration.implementing.methods.checkbox")
            );
            Utils.renameCheckbox(
                    findWhatPanel,
                    JavaBundle.message("find.what.overriding.methods.checkbox"),
                    KotlinBundle.message("find.declaration.overriding.methods.checkbox")
            );
        }

        return findWhatPanel;
    }

    @Override
    protected void addUsagesOptions(JPanel optionsPanel) {
        super.addUsagesOptions(optionsPanel);

        if (!Utils.renameCheckbox(
                optionsPanel,
                JavaBundle.message("find.options.include.overloaded.methods.checkbox"),
                KotlinBundle.message("find.declaration.include.overloaded.methods.checkbox")
        )) {
            addCheckboxToPanel(
                    KotlinBundle.message("find.declaration.include.overloaded.methods.checkbox"),
                    FindSettings.getInstance().isSearchOverloadedMethods(),
                    optionsPanel,
                    false
            );
        }
        PsiElement element = LightClassUtilsKt.getUnwrapped(getPsiElement());
        //noinspection ConstantConditions
        KtDeclaration function = element instanceof KtNamedDeclaration
                                 ? (KtNamedDeclaration) element
                                 : ((KtLightMethod) element).getKotlinOrigin();

        boolean isActual = function != null && PsiUtilsKt.hasActualModifier(function);
        KotlinFunctionFindUsagesOptions options = getFindUsagesOptions();
        if (isActual) {
            expectedUsages = addCheckboxToPanel(
                    KotlinBundle.message("find.usages.checkbox.name.expected.functions"),
                    options.getSearchExpected(),
                    optionsPanel,
                    false
            );
        }
    }

    @Override
    public void calcFindUsagesOptions(JavaMethodFindUsagesOptions options) {
        super.calcFindUsagesOptions(options);

        KotlinFunctionFindUsagesOptions kotlinOptions = (KotlinFunctionFindUsagesOptions) options;
        if (expectedUsages != null) {
            kotlinOptions.setSearchExpected(expectedUsages.isSelected());
        }
    }
}
