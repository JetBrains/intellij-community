// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.findUsages.dialogs;

import com.intellij.find.FindSettings;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesDialog;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.StateRestoringCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.KotlinBundle;
import org.jetbrains.kotlin.idea.findUsages.KotlinPropertyFindUsagesOptions;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.*;
import org.jetbrains.kotlin.psi.psiUtil.PsiUtilsKt;

import javax.swing.*;
import java.awt.*;

public class KotlinFindPropertyUsagesDialog extends JavaFindUsagesDialog<KotlinPropertyFindUsagesOptions> {
    public KotlinFindPropertyUsagesDialog(
            PsiElement element,
            Project project,
            KotlinPropertyFindUsagesOptions findUsagesOptions,
            boolean toShowInNewTab,
            boolean mustOpenInNewTab,
            boolean isSingleFile,
            FindUsagesHandler handler
    ) {
        super(element, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
    }

    private StateRestoringCheckBox readAccesses;
    private StateRestoringCheckBox writeAccesses;
    private StateRestoringCheckBox searchForBase;
    private StateRestoringCheckBox overrideUsages;
    private StateRestoringCheckBox expectedUsages;
    private StateRestoringCheckBox searchInOverridingMethods;

    @NotNull
    @Override
    protected KotlinPropertyFindUsagesOptions getFindUsagesOptions() {
        return (KotlinPropertyFindUsagesOptions) myFindUsagesOptions;
    }

    @Override
    public JComponent getPreferredFocusedControl() {
        return myCbToSkipResultsWhenOneUsage;
    }

    @Override
    public void calcFindUsagesOptions(KotlinPropertyFindUsagesOptions options) {
        super.calcFindUsagesOptions(options);

        options.isReadAccess = isSelected(readAccesses);
        options.isWriteAccess = isSelected(writeAccesses);
        options.isSearchForBaseAccessors = isSelected(searchForBase);
        options.isSearchInOverridingMethods = isSelected(searchInOverridingMethods);
        options.setSearchOverrides(isSelected(overrideUsages));
        if (expectedUsages != null) {
            options.setSearchExpected(expectedUsages.isSelected());
        }
    }

    @Override
    protected JPanel createFindWhatPanel() {
        JPanel findWhatPanel = new JPanel();
        findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

        KotlinPropertyFindUsagesOptions options = getFindUsagesOptions();

        readAccesses = addCheckboxToPanel(
                KotlinBundle.message("find.declaration.property.readers.checkbox"),
                options.isReadAccess,
                findWhatPanel,
                true
        );
        writeAccesses = addCheckboxToPanel(
                KotlinBundle.message("find.declaration.property.writers.checkbox"),
                options.isWriteAccess,
                findWhatPanel,
                true
        );

        return findWhatPanel;
    }

    @Override
    public void configureLabelComponent(@NotNull SimpleColoredComponent coloredComponent) {
        Utils.configureLabelComponent(coloredComponent, (KtNamedDeclaration) getPsiElement());
    }

    @Override
    protected void addUsagesOptions(JPanel optionsPanel) {
        super.addUsagesOptions(optionsPanel);

        KtNamedDeclaration property = (KtNamedDeclaration) getPsiElement();
        if (property.hasModifier(KtTokens.OVERRIDE_KEYWORD) || property.hasModifier(KtTokens.OPEN_KEYWORD) ||
            property instanceof KtParameter && !((KtParameter)property).hasValOrVar()) {
            searchForBase = createCheckbox(JavaBundle.message("find.options.include.accessors.base.checkbox"),
                                           getFindUsagesOptions().isSearchForBaseAccessors, true);
            JComponent decoratedCheckbox = new ComponentPanelBuilder(searchForBase).
                    withComment(JavaBundle.message("find.options.include.accessors.base.checkbox.comment")).createPanel();
            decoratedCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
            optionsPanel.add(decoratedCheckbox);
        }

        if (property instanceof KtParameter) {
            searchInOverridingMethods = addCheckboxToPanel(JavaBundle.message("find.options.search.overriding.methods.checkbox"),
                                                           getFindUsagesOptions().isSearchInOverridingMethods, optionsPanel, true);
        }

        boolean isAbstract = property.hasModifier(KtTokens.ABSTRACT_KEYWORD);
        boolean isOpen = property.hasModifier(KtTokens.OPEN_KEYWORD);
        if (isOpen || isAbstract) {
            overrideUsages = addCheckboxToPanel(
                    isAbstract
                    ? KotlinBundle.message("find.declaration.implementing.properties.checkbox")
                    : KotlinBundle.message("find.declaration.overriding.properties.checkbox"),
                    FindSettings.getInstance().isSearchOverloadedMethods(),
                    optionsPanel,
                    false
            );
        }
        boolean isActual = PsiUtilsKt.hasActualModifier(property);
        KotlinPropertyFindUsagesOptions options = getFindUsagesOptions();
        if (isActual) {
            expectedUsages = addCheckboxToPanel(
                    KotlinBundle.message("find.usages.checkbox.name.expected.properties"),
                    options.getSearchExpected(),
                    optionsPanel,
                    false
            );
        }

        if (isDataClassConstructorProperty(property)) {
            JCheckBox dataClassComponentCheckBox =
                    new JCheckBox(KotlinBundle.message("find.usages.checkbox.text.fast.data.class.component.search"));
            dataClassComponentCheckBox.setToolTipText(KotlinBundle.message(
                    "find.usages.tool.tip.text.disable.search.for.data.class.components.and.destruction.declarations.project.wide.setting"));
            Project project = property.getProject();
            dataClassComponentCheckBox.setSelected(getDisableComponentAndDestructionSearch(project));
            optionsPanel.add(dataClassComponentCheckBox);
            dataClassComponentCheckBox.addActionListener(
                    ___ -> setDisableComponentAndDestructionSearch(project, dataClassComponentCheckBox.isSelected())
            );
        }
    }

    @Override
    protected void update() {
        setOKActionEnabled(isSelected(readAccesses) || isSelected(writeAccesses));
    }

    private static final boolean disableComponentAndDestructionSearchDefault = false;
    private static final String optionName = "kotlin.disable.search.component.and.destruction";

    public static boolean getDisableComponentAndDestructionSearch(Project project) {
        return PropertiesComponent.getInstance(project).getBoolean(optionName, disableComponentAndDestructionSearchDefault);
    }

    public static void setDisableComponentAndDestructionSearch(Project project, boolean value) {
        PropertiesComponent.getInstance(project).setValue(optionName, value, disableComponentAndDestructionSearchDefault);
    }

    private static boolean isDataClassConstructorProperty(KtNamedDeclaration declaration) {
        if (declaration instanceof KtParameter) {
            PsiElement parent = declaration.getParent();
            if (parent instanceof KtParameterList) {
                parent = parent.getParent();
                if (parent instanceof KtPrimaryConstructor) {
                    parent = parent.getParent();
                    if (parent instanceof KtClass) {
                        return ((KtClass)parent).isData();
                    }
                }
            }
        }
        return false;
    }
}
