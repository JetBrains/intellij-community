/*
 * Copyright 2001-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.PsiElementClassMember;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.TabbedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.generate.tostring.GenerateToStringClassFilter;
import org.jetbrains.java.generate.config.Config;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.template.toString.ToStringTemplatesManager;
import org.jetbrains.java.generate.view.TemplatesPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The action-handler that does the code generation.
 */
public class GenerateToStringActionHandlerImpl implements GenerateToStringActionHandler, CodeInsightActionHandler {
    private static final Logger logger = Logger.getInstance("#GenerateToStringActionHandlerImpl");

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        PsiClass clazz = getSubjectClass(editor, file);
        assert clazz != null;

        doExecuteAction(project, clazz, editor);
    }


    public void executeActionQuickFix(final Project project, final PsiClass clazz) {
        doExecuteAction(project, clazz, null);
    }

    private static void doExecuteAction(@NotNull final Project project, @NotNull final PsiClass clazz, final Editor editor) {
        logger.debug("+++ doExecuteAction - START +++");

        if (logger.isDebugEnabled()) {
            logger.debug("Current project " + project.getName());
        }

        final PsiElementClassMember[] dialogMembers = buildMembersToShow(clazz);

        final MemberChooserHeaderPanel header = new MemberChooserHeaderPanel(clazz);
        logger.debug("Displaying member chooser dialog");

        final MemberChooser<PsiElementClassMember> chooser =
            new MemberChooser<PsiElementClassMember>(dialogMembers, true, true, project, PsiUtil.isLanguageLevel5OrHigher(clazz), header) {
                @Nullable
                @Override
                protected String getHelpId() {
                    return "editing.altInsert.tostring";
                }
            };
        //noinspection DialogTitleCapitalization
        chooser.setTitle("Generate toString()");

        chooser.setCopyJavadocVisible(false);
        chooser.selectElements(dialogMembers);
        header.setChooser(chooser);
        chooser.show();

        if (DialogWrapper.OK_EXIT_CODE == chooser.getExitCode()) {
            Collection<PsiMember> selectedMembers = GenerationUtil.convertClassMembersToPsiMembers(chooser.getSelectedElements());

            final TemplateResource template = header.getSelectedTemplate();
            ToStringTemplatesManager.getInstance().setDefaultTemplate(template);

            if (template.isValidTemplate()) {
                WriteAction.run(() -> {
                    try {
                        new GenerateToStringWorker(clazz, editor, chooser.isInsertOverrideAnnotation()).execute(selectedMembers, template);
                    }
                    catch (Exception e) {
                        GenerationUtil.handleException(project, e);
                    }
                });
            }
            else {
                HintManager.getInstance().showErrorHint(editor, "toString() template '" + template.getFileName() + "' is invalid");
            }
        }

        logger.debug("+++ doExecuteAction - END +++");
    }

    public static void updateDialog(PsiClass clazz, MemberChooser<PsiElementClassMember> dialog) {
        final PsiElementClassMember[] members = buildMembersToShow(clazz);
        dialog.resetElements(members);
        dialog.selectElements(members);
    }

    private static PsiElementClassMember[] buildMembersToShow(PsiClass clazz) {
        Config config = GenerateToStringContext.getConfig();
        PsiField[] filteredFields = GenerateToStringUtils.filterAvailableFields(clazz, config.getFilterPattern());
        if (logger.isDebugEnabled()) logger.debug("Number of fields after filtering: " + filteredFields.length);
        PsiMethod[] filteredMethods;
        if (config.enableMethods) {
            // filter methods as it is enabled from config
            filteredMethods = GenerateToStringUtils.filterAvailableMethods(clazz, config.getFilterPattern());
            if (logger.isDebugEnabled()) logger.debug("Number of methods after filtering: " + filteredMethods.length);
        } else {
          filteredMethods = PsiMethod.EMPTY_ARRAY;
        }

        return GenerationUtil.combineToClassMemberList(filteredFields, filteredMethods);
    }

    @Nullable
    private static PsiClass getSubjectClass(Editor editor, final PsiFile file) {
        if (file == null) return null;

        int offset = editor.getCaretModel().getOffset();
        PsiElement context = file.findElementAt(offset);

        if (context == null) return null;

        PsiClass clazz = PsiTreeUtil.getParentOfType(context, PsiClass.class, false);
        if (clazz == null) {
            return null;
        }

        //exclude interfaces, non-java classes etc
        for (GenerateToStringClassFilter filter : GenerateToStringClassFilter.EP_NAME.getExtensions()) {
            if (!filter.canGenerateToString(clazz)) return null;
        }
        return clazz;
    }

    public static class MemberChooserHeaderPanel extends JPanel {
        private MemberChooser<PsiElementClassMember> chooser;
        private final JComboBox<TemplateResource> comboBox;

        public void setChooser(MemberChooser chooser) {
            this.chooser = chooser;
        }

        public MemberChooserHeaderPanel(final PsiClass clazz) {
            super(new GridBagLayout());

            final Collection<TemplateResource> templates = ToStringTemplatesManager.getInstance().getAllTemplates();
            final TemplateResource[] all = templates.toArray(new TemplateResource[templates.size()]);

            final JButton settingsButton = new JButton("Settings");
            settingsButton.setMnemonic(KeyEvent.VK_S);

            comboBox = new ComboBox<>(all);
            final JavaPsiFacade instance = JavaPsiFacade.getInstance(clazz.getProject());
            final GlobalSearchScope resolveScope = clazz.getResolveScope();
            final ListCellRendererWrapper<TemplateResource> renderer = new ListCellRendererWrapper<TemplateResource>() {
              @Override
              public void customize(JList list, TemplateResource value, int index, boolean selected, boolean hasFocus) {
                setText(value.getName());
                final String className = value.getClassName();
                if (className != null && instance.findClass(className, resolveScope) == null) {
                  setForeground(JBColor.RED);
                }
              }
            };
            comboBox.setRenderer(renderer);
            settingsButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                  final TemplatesPanel ui = new TemplatesPanel(clazz.getProject());
                  Disposable disposable = Disposer.newDisposable();
                  Configurable composite = new TabbedConfigurable(disposable) {
                        protected List<Configurable> createConfigurables() {
                            List<Configurable> res = new ArrayList<>();
                            res.add(new GenerateToStringConfigurable(clazz.getProject()));
                            res.add(ui);
                            return res;
                        }

                        public String getDisplayName() {
                            return "toString() Generation Settings";
                        }

                        public String getHelpTopic() {
                            return "editing.altInsert.tostring.settings";
                        }

                        @Override
                        public void apply() throws ConfigurationException {
                            super.apply();
                            updateDialog(clazz, chooser);

                            comboBox.removeAllItems();
                            for (TemplateResource resource : ToStringTemplatesManager.getInstance().getAllTemplates()) {
                              comboBox.addItem(resource);
                            }
                            comboBox.setSelectedItem(ToStringTemplatesManager.getInstance().getDefaultTemplate());
                        }
                    };

                    ShowSettingsUtil.getInstance().editConfigurable(MemberChooserHeaderPanel.this, composite, () -> ui.selectItem(ToStringTemplatesManager.getInstance().getDefaultTemplate()));
                  Disposer.dispose(disposable);
                }
            });

            comboBox.setSelectedItem(ToStringTemplatesManager.getInstance().getDefaultTemplate());

            final JLabel templatesLabel = new JLabel("Template: ");
            templatesLabel.setDisplayedMnemonic('T');
            templatesLabel.setLabelFor(comboBox);

            final GridBagConstraints constraints = new GridBagConstraints();
            constraints.anchor = GridBagConstraints.BASELINE;
            constraints.gridx = 0;
            add(templatesLabel, constraints);
            constraints.gridx = 1;
            constraints.weightx = 1.0;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            add(comboBox, constraints);
            constraints.gridx = 2;
            constraints.weightx = 0.0;
            add(settingsButton, constraints);
        }

        public TemplateResource getSelectedTemplate() {
            return (TemplateResource) comboBox.getSelectedItem();
        }
    }
}
