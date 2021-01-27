// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.findUsages.dialogs;

import com.intellij.find.findUsages.FindClassUsagesDialog;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.findUsages.JavaClassFindUsagesOptions;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiModifier;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.StateRestoringCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.idea.KotlinIndependentBundle;
import org.jetbrains.kotlin.idea.findUsages.KotlinClassFindUsagesOptions;
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.psiUtil.PsiUtilsKt;

import javax.swing.*;

import static org.jetbrains.kotlin.asJava.LightClassUtilsKt.toLightClass;

public class KotlinFindClassUsagesDialog extends FindClassUsagesDialog {
    private StateRestoringCheckBox constructorUsages;
    private StateRestoringCheckBox derivedClasses;
    private StateRestoringCheckBox derivedTraits;
    private StateRestoringCheckBox expectedUsages;

    public KotlinFindClassUsagesDialog(
            KtClassOrObject classOrObject,
            Project project,
            FindUsagesOptions findUsagesOptions,
            boolean toShowInNewTab,
            boolean mustOpenInNewTab,
            boolean isSingleFile,
            FindUsagesHandler handler
    ) {
        super(getRepresentingPsiClass(classOrObject), project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
    }

    private static final Key<KtClassOrObject> ORIGINAL_CLASS = Key.create("ORIGINAL_CLASS");

    @NotNull
    private static PsiClass getRepresentingPsiClass(@NotNull KtClassOrObject classOrObject) {
        PsiClass lightClass = toLightClass(classOrObject);
        if (lightClass != null) return lightClass;

        // TODO: Remove this code when light classes are generated for builtins
      PsiElementFactory factory = PsiElementFactory.getInstance(classOrObject.getProject());

        String name = classOrObject.getName();
        if (name == null || name.isEmpty()) {
            name = KotlinIndependentBundle.message("find.usages.class.name.anonymous");
        }

        PsiClass javaClass;
        if (classOrObject instanceof KtClass) {
            KtClass klass = (KtClass) classOrObject;
            javaClass = !klass.isInterface()
                        ? factory.createClass(name)
                        : klass.isAnnotation()
                          ? factory.createAnnotationType(name)
                          : factory.createInterface(name);
        }
        else {
            javaClass = factory.createClass(name);
        }

        //noinspection ConstantConditions
        javaClass.getModifierList().setModifierProperty(
                PsiModifier.FINAL,
                !(classOrObject instanceof KtClass && KotlinSearchUsagesSupport.Companion.isInheritable((KtClass) classOrObject))
        );

        javaClass.putUserData(ORIGINAL_CLASS, classOrObject);

        return javaClass;
    }

    @Override
    protected JPanel createFindWhatPanel() {
        JPanel findWhatPanel = super.createFindWhatPanel();
        assert findWhatPanel != null;

        Utils.renameCheckbox(
          findWhatPanel,
          JavaBundle.message("find.what.methods.usages.checkbox"),
          KotlinIndependentBundle.message("find.declaration.functions.usages.checkbox")
        );
        Utils.renameCheckbox(
          findWhatPanel,
          JavaBundle.message("find.what.fields.usages.checkbox"),
          KotlinIndependentBundle.message("find.declaration.properties.usages.checkbox")
        );
        Utils.removeCheckbox(findWhatPanel, JavaBundle.message("find.what.implementing.classes.checkbox"));
        Utils.removeCheckbox(findWhatPanel, JavaBundle.message("find.what.derived.interfaces.checkbox"));
        Utils.removeCheckbox(findWhatPanel, JavaBundle.message("find.what.derived.classes.checkbox"));

        derivedClasses = addCheckboxToPanel(
          KotlinIndependentBundle.message("find.declaration.derived.classes.checkbox"),
          getFindUsagesOptions().isDerivedClasses,
          findWhatPanel,
          true
        );
        derivedTraits = addCheckboxToPanel(
          KotlinIndependentBundle.message("find.declaration.derived.interfaces.checkbox"),
          getFindUsagesOptions().isDerivedInterfaces,
          findWhatPanel,
          true
        );
        constructorUsages = addCheckboxToPanel(
          KotlinIndependentBundle.message("find.declaration.constructor.usages.checkbox"),
          getFindUsagesOptions().getSearchConstructorUsages(),
          findWhatPanel,
          true
        );
        return findWhatPanel;
    }

    @NotNull
    @Override
    protected KotlinClassFindUsagesOptions getFindUsagesOptions() {
        return (KotlinClassFindUsagesOptions) super.getFindUsagesOptions();
    }

    @Nullable
    private KtClassOrObject getOriginalClass() {
        PsiElement klass = LightClassUtilsKt.getUnwrapped(getPsiElement());
        if (klass == null) {
            return null;
        }

        return klass instanceof KtClassOrObject
               ? (KtClassOrObject) klass
               : klass.getUserData(ORIGINAL_CLASS);
    }

    @Override
    protected void addUsagesOptions(JPanel optionsPanel) {
        super.addUsagesOptions(optionsPanel);

        KtClassOrObject klass = getOriginalClass();
        boolean isActual = klass != null && PsiUtilsKt.hasActualModifier(klass);
        KotlinClassFindUsagesOptions options = getFindUsagesOptions();
        if (isActual) {
            expectedUsages = addCheckboxToPanel(
              KotlinIndependentBundle.message("find.usages.checkbox.name.expected.classes"),
              options.getSearchExpected(),
              optionsPanel,
              false
            );
        }
    }

    @Override
    public void configureLabelComponent(@NotNull SimpleColoredComponent coloredComponent) {
        KtClassOrObject originalClass = getOriginalClass();
        if (originalClass != null) {
            coloredComponent.append(KotlinSearchUsagesSupport.Companion.formatClass(originalClass));
        }
    }

    @Override
    protected void update() {
        super.update();
        if (!isOKActionEnabled() && (constructorUsages.isSelected() || derivedTraits.isSelected() || derivedClasses.isSelected())) {
            setOKActionEnabled(true);
        }
    }

    @Override
    public void calcFindUsagesOptions(JavaClassFindUsagesOptions options) {
        super.calcFindUsagesOptions(options);

        KotlinClassFindUsagesOptions kotlinOptions = (KotlinClassFindUsagesOptions) options;
        kotlinOptions.setSearchConstructorUsages(constructorUsages.isSelected());
        kotlinOptions.isDerivedClasses = derivedClasses.isSelected();
        kotlinOptions.isDerivedInterfaces = derivedTraits.isSelected();
        if (expectedUsages != null) {
            kotlinOptions.setSearchExpected(expectedUsages.isSelected());
        }
    }
}
