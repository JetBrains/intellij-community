// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.move.moveMethod;

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeJavaClassChooserDialog;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.kotlin.idea.KotlinIconProvider;
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider;
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester;
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNewDeclarationNameValidator;
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle;
import org.jetbrains.kotlin.idea.completion.CompletionUtilsKt;
import org.jetbrains.kotlin.idea.core.completion.DescriptorBasedDeclarationLookupObject;
import org.jetbrains.kotlin.idea.core.completion.PackageLookupObject;
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringUtilKt;
import org.jetbrains.kotlin.idea.refactoring.ui.KotlinTypeReferenceEditorComboWithBrowseButton;
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers;
import org.jetbrains.kotlin.psi.*;
import org.jetbrains.kotlin.psi.psiUtil.KtPsiUtilKt;
import org.jetbrains.kotlin.renderer.DescriptorRenderer;
import org.jetbrains.kotlin.types.KotlinType;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static org.jetbrains.kotlin.name.Name.isValidIdentifier;

public class MoveKotlinMethodDialog extends RefactoringDialog {
    private static final String RECENTS_KEY = MoveKotlinMethodDialog.class.getName() + ".RECENTS_KEY";
    private final KtNamedFunction method;
    private final Map<KtNamedDeclaration, KtClass> variableToClassMap;
    private final KtNamedDeclaration[] variables;
    private final Map<KtClass, Set<KtNamedDeclaration>> thisClassesToMembers;
    private final KtClassOrObject targetContainer;
    private final Map<KtClass, EditorTextField> oldClassParameterNameFields;
    private KtNamedDeclaration selectedTarget;
    private JPanel mainPanel;
    private JRadioButton toClassRadioButton;
    private JRadioButton toObjectRadioButton;
    private JPanel targetObjectChooserPanel;
    private KotlinTypeReferenceEditorComboWithBrowseButton targetObjectChooser;
    private JList<KtNamedDeclaration> targetVariableList;
    private JPanel targetVariableListPanel;
    private JPanel parametersPanel;
    private JCheckBox openInEditorCheckBox;

    public MoveKotlinMethodDialog(
            @NotNull KtNamedFunction method,
            @NotNull Map<KtNamedDeclaration, KtClass> variableToClassMap,
            @Nullable KtClassOrObject targetContainer
    ) {
        super(method.getProject(), true);

        this.method = method;
        this.variableToClassMap = variableToClassMap;
        this.targetContainer = targetContainer;
        this.thisClassesToMembers = MoveKotlinMethodProcessorKt.getThisClassesToMembers(method);
        this.variables = variableToClassMap.keySet().toArray(new KtNamedDeclaration[0]);
        this.oldClassParameterNameFields = new HashMap<>();

        init();
        setTitle(KotlinBundle.message("title.move.method"));
        initTargetObjectChooser();
        initTargetVariableList();
        initParametersPanel();
        initButtons();
    }

    @Override
    protected void doAction() {
        if (toClassRadioButton.isSelected()) selectedTarget = targetVariableList.getSelectedValue();
        if (toObjectRadioButton.isSelected() && selectedTarget == null) {
            setErrorText(KotlinBundle.message("text.no.destination.object.specified"));
            return;
        }

        Map<KtClass, String> oldClassParameterNames = new LinkedHashMap<>();
        for (Map.Entry<KtClass, EditorTextField> entry : oldClassParameterNameFields.entrySet()) {
            EditorTextField field = entry.getValue();
            if (!isValidIdentifier(field.getText())) {
                setErrorText(KotlinBundle.message("parameter.name.is.invalid", field.getText()));
                return;
            }
            if (field.isEnabled()) {
                oldClassParameterNames.put(entry.getKey(), field.getText());
            }
        }

        MoveKotlinMethodProcessor processor =
                new MoveKotlinMethodProcessor(method, selectedTarget, oldClassParameterNames, openInEditorCheckBox.isSelected());
        invokeRefactoring(processor);
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return mainPanel;
    }

    private void initButtons() {
        toClassRadioButton.addActionListener(e -> enableTargetChooser());
        toObjectRadioButton.addActionListener(e -> enableTargetChooser());

        if (variables.length != 0 && !(targetContainer instanceof KtObjectDeclaration)) {
            toClassRadioButton.setSelected(true);
        }
        else {
            toObjectRadioButton.setSelected(true);
            if (variables.length == 0) {
                toClassRadioButton.setEnabled(false);
            }
        }

        enableTextFields();
        enableTargetChooser();
    }

    private void initTargetVariableList() {
        AbstractListModel<KtNamedDeclaration> listModel = new AbstractListModel<>() {
            @Override
            public int getSize() {
                return variables.length;
            }

            @Override
            public KtNamedDeclaration getElementAt(int index) {
                return variables[index];
            }
        };
        targetVariableList = new JBList<>(listModel);
        DefaultListCellRenderer listCellRenderer = new DefaultListCellRenderer() {
            private final DescriptorRenderer renderer = IdeDescriptorRenderers.SOURCE_CODE_TYPES_WITH_SHORT_NAMES;

            @Override
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus
            ) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof KtNamedDeclaration variable) {
                  setIcon(KotlinIconProvider.Companion.getBaseIcon(variable));
                    setText(variable.getName());
                    KotlinType type = MoveKotlinMethodProcessorKt.type(variable);
                    if (type != null) {
                        @NlsSafe String renderType = renderer.renderType(type);
                        setText(getText() + ": " + renderType);
                    }
                }
                return this;
            }
        };

        targetVariableList.setCellRenderer(listCellRenderer);
        targetVariableList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        int defaultVariableIndex = -1;
        for (int i = 0; i < variables.length; i++) {
            if (variableToClassMap.get(variables[i]) == targetContainer) {
                defaultVariableIndex = i;
            }
        }
        targetVariableList.setSelectedIndex(defaultVariableIndex != -1 ? defaultVariableIndex : 0);
        targetVariableList.getSelectionModel().addListSelectionListener(e -> enableTextFields());

        JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(targetVariableList);
        targetVariableListPanel.add(scrollPane);
    }

    private void initTargetObjectChooser() {
        targetObjectChooser = new KotlinTypeReferenceEditorComboWithBrowseButton(
                e -> {
                    TreeClassChooser chooser = createTreeClassChooser();
                    chooser.selectDirectory(method.getContainingFile().getContainingDirectory());
                    chooser.showDialog();
                    PsiClass psiClass = chooser.getSelected();
                    if (psiClass instanceof KtLightClassForSourceDeclaration) {
                        selectedTarget = ((KtLightClassForSourceDeclaration) psiClass).getKotlinOrigin();
                        targetObjectChooser.setText(Objects.requireNonNull(psiClass.getQualifiedName()));
                    }
                },
                (targetContainer == null || targetContainer.getFqName() == null) ? null : targetContainer.getFqName().asString(),
                targetContainer == null ? Objects.requireNonNull(KtPsiUtilKt.getContainingClassOrObject(method)) : targetContainer,
                RECENTS_KEY
        );
        KtTypeCodeFragment codeFragment = targetObjectChooser.getCodeFragment();
        if (codeFragment != null) {
            CompletionUtilsKt.setExtraCompletionFilter(
                    codeFragment,
                    lookupElement -> {
                        Object lookupObject = lookupElement.getObject();
                        if (!(lookupObject instanceof DescriptorBasedDeclarationLookupObject)) return false;
                        PsiElement psiElement = ((DescriptorBasedDeclarationLookupObject) lookupObject).getPsiElement();
                        if (lookupObject instanceof PackageLookupObject) return true;
                        return (psiElement instanceof KtObjectDeclaration) && KotlinCommonRefactoringUtilKt.canRefactorElement(psiElement);
                    }
            );
        }
        targetObjectChooserPanel.add(targetObjectChooser);
    }

    private void enableTextFields() {
        for (EditorTextField textField : oldClassParameterNameFields.values()) {
            textField.setEnabled(true);
        }
        if (toClassRadioButton.isSelected()) {
            KtNamedDeclaration variable = variables[targetVariableList.getSelectedIndex()];
            KtClassOrObject containingClass = KtPsiUtilKt.getContainingClassOrObject(variable);
            if (!(containingClass instanceof KtClass)) return;
            if (!(variable instanceof KtParameter) || ((KtParameter) variable).hasValOrVar()) {
                Set<KtNamedDeclaration> members = thisClassesToMembers.get(containingClass);
                if (members != null && members.size() == 1 && members.contains(variable)) {
                    EditorTextField field = oldClassParameterNameFields.get(containingClass);
                    if (field != null) field.setEnabled(false);
                }
            }
        }
    }

    private void enableTargetChooser() {
        if (toClassRadioButton.isSelected()) {
            targetVariableList.setEnabled(true);
            targetObjectChooser.setEnabled(false);
        }
        else {
            targetVariableList.setEnabled(false);
            targetObjectChooser.setEnabled(true);
        }
        enableTextFields();
    }

    private void initParametersPanel() {
        if (thisClassesToMembers.isEmpty()) return;
        parametersPanel.setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true));
        Fe10KotlinNewDeclarationNameValidator validator = new Fe10KotlinNewDeclarationNameValidator(
                (PsiElement) method,
                null,
                KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER,
                new ArrayList<>()
        );

        for (KtClass ktClass : thisClassesToMembers.keySet()) {
            KotlinType type = MoveKotlinMethodProcessorKt.defaultType(ktClass);
            if (type == null) continue;
            @SuppressWarnings("DialogTitleCapitalization")
            String text = KotlinBundle.message("title.select.a.name.for.this.parameter", ktClass.getName());
            parametersPanel.add(new TitledSeparator(text, null));
            List<String> suggestedNames = Fe10KotlinNameSuggester.INSTANCE.suggestNamesByType(type, validator, null);
            String suggestedName = suggestedNames.isEmpty() ? "parameter" : suggestedNames.get(0);
            EditorTextField field = new EditorTextField(suggestedName, myProject, KotlinFileType.INSTANCE);
            oldClassParameterNameFields.put(ktClass, field);
            parametersPanel.add(field);
        }
    }

    private TreeJavaClassChooserDialog createTreeClassChooser() {
        return new TreeJavaClassChooserDialog(
                KotlinBundle.message("title.choose.destination.object"),
                myProject,
                GlobalSearchScope.projectScope(myProject),
                aClass -> {
                    if (!(aClass instanceof KtLightClassForSourceDeclaration)) return false;
                    KtClassOrObject ktClassOrObject = ((KtLightClassForSourceDeclaration) aClass).getKotlinOrigin();
                    return ktClassOrObject instanceof KtObjectDeclaration && !((KtObjectDeclaration) ktClassOrObject).isObjectLiteral();
                },
                null,
                null,
                true
        );
    }
}

