/*
 * Copyright 2001-2012 the original author or authors.
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
package org.jetbrains.generate.tostring;

import com.intellij.codeInsight.generation.PsiElementClassMember;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.ide.util.MemberChooser;
import com.intellij.ide.util.MemberChooserBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.TabbedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.generate.tostring.config.Config;
import org.jetbrains.generate.tostring.template.TemplateResource;
import org.jetbrains.generate.tostring.template.TemplatesManager;
import org.jetbrains.generate.tostring.view.TemplatesPanel;

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
public class GenerateToStringActionHandlerImpl extends EditorWriteActionHandler implements GenerateToStringActionHandler {
    private static final Logger logger = Logger.getInstance("#org.jetbrains.generate.tostring.GenerateToStringActionHandlerImpl");

    public void executeWriteAction(Editor editor, DataContext dataContext) {
        final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
        assert project != null;

        PsiClass clazz = getSubjectClass(editor, dataContext);
        assert clazz != null;

        doExecuteAction(project, clazz, editor);
    }


    public void executeActionQuickFix(final Project project, final PsiClass clazz) {
        doExecuteAction(project, clazz, null);
    }

    /**
     * Entry for performing the action and code generation.
     *
     * @param project      the project, must not be <tt>null<tt>
     * @param clazz        the class, must not be <tt>null<tt>
     */
    private static void doExecuteAction(@NotNull final Project project, @NotNull final PsiClass clazz, final Editor editor) {
        logger.debug("+++ doExecuteAction - START +++");

        if (logger.isDebugEnabled()) {
            logger.debug("Current project " + project.getName());
        }

        final PsiElementClassMember[] dialogMembers = buildMembersToShow(clazz);
        if (dialogMembers.length == 0) {
          HintManager.getInstance().showErrorHint(editor, "No members to include in toString() have been found");
          return;
        }

        final MemberChooserBuilder<PsiElementClassMember> builder = new MemberChooserBuilder<PsiElementClassMember>(project);
        final MemberChooserHeaderPanel header = new MemberChooserHeaderPanel(clazz);
        builder.setHeaderPanel(header);
        builder.overrideAnnotationVisible(PsiUtil.isLanguageLevel5OrHigher(clazz));
        builder.setTitle("Generate toString()");

        logger.debug("Displaying member chooser dialog");
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              if (project.isDisposed()) return;
              final MemberChooser dialog = builder.createBuilder(dialogMembers);
              dialog.setCopyJavadocVisible(false);
              dialog.selectElements(dialogMembers);
              header.setChooser(dialog);
                dialog.show();

                if (DialogWrapper.OK_EXIT_CODE == dialog.getExitCode()) {
                    Collection<PsiMember> selectedMembers = GenerateToStringUtils.convertClassMembersToPsiMembers(dialog.getSelectedElements());

                    final TemplateResource template = header.getSelectedTemplate();
                    TemplatesManager.getInstance().setDefaultTemplate(template);

                    if (template.isValidTemplate()) {
                        GenerateToStringWorker.executeGenerateActionLater(clazz, editor, selectedMembers, template, dialog.isInsertOverrideAnnotation());
                    }
                    else {
                        HintManager.getInstance().showErrorHint(editor, "toString() template '" + template.getFileName() + "' is invalid");
                    }
                }
            }
        });

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

        return GenerateToStringUtils.combineToClassMemberList(filteredFields, filteredMethods);
    }

    @Override
    public boolean isEnabled(Editor editor, DataContext dataContext) {
        return getSubjectClass(editor, dataContext) != null;
    }

    @Nullable
    private static PsiClass getSubjectClass(Editor editor, DataContext dataContext) {
        PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
        if (file == null) return null;

        int offset = editor.getCaretModel().getOffset();
        PsiElement context = file.findElementAt(offset);

        if (context == null) return null;

        PsiClass clazz = PsiTreeUtil.getParentOfType(context, PsiClass.class, false);
        if (clazz == null) {
            return null;
        }

        // must not be an interface
        return clazz.isInterface() ? null : clazz;
    }

    public static class MemberChooserHeaderPanel extends JPanel {
        private MemberChooser chooser;
        private final JComboBox comboBox;

        public void setChooser(MemberChooser chooser) {
            this.chooser = chooser;
        }

        public MemberChooserHeaderPanel(final PsiClass clazz) {
            super(new BorderLayout());

            final Collection<TemplateResource> templates = TemplatesManager.getInstance().getAllTemplates();
            final TemplateResource[] all = templates.toArray(new TemplateResource[templates.size()]);

            final JButton settingsButton = new JButton("Settings");
            settingsButton.setMnemonic(KeyEvent.VK_S);

            comboBox = new JComboBox(all);
            settingsButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    final TemplatesPanel ui = new TemplatesPanel();
                  Disposable disposable = Disposer.newDisposable();
                  Configurable composite = new TabbedConfigurable(disposable) {
                        protected List<Configurable> createConfigurables() {
                            List<Configurable> res = new ArrayList<Configurable>();
                            res.add(new GenerateToStringConfigurable(clazz.getProject()));
                            res.add(ui);
                            return res;
                        }

                        public String getDisplayName() {
                            return "toString() Generation Settings";
                        }

                        public String getHelpTopic() {
                            return null; // TODO:
                        }

                        @Override
                        public void apply() throws ConfigurationException {
                            super.apply();
                            updateDialog(clazz, chooser);

                            comboBox.removeAllItems();
                            for (TemplateResource resource : TemplatesManager.getInstance().getAllTemplates()) {
                              comboBox.addItem(resource);
                            }
                            comboBox.setSelectedItem(TemplatesManager.getInstance().getDefaultTemplate());
                        }
                    };

                    ShowSettingsUtil.getInstance().editConfigurable(MemberChooserHeaderPanel.this, composite, new Runnable() {
                        public void run() {
                            ui.selectItem(TemplatesManager.getInstance().getDefaultTemplate());
                        }
                    });
                  Disposer.dispose(disposable);
                }
            });

            add(settingsButton, BorderLayout.EAST);
            add(comboBox, BorderLayout.CENTER);
            comboBox.setSelectedItem(TemplatesManager.getInstance().getDefaultTemplate());

            final JLabel templatesLabel = new JLabel("Template: ");
            templatesLabel.setDisplayedMnemonic('T');
            templatesLabel.setLabelFor(comboBox);

            add(templatesLabel, BorderLayout.WEST);
        }

        public TemplateResource getSelectedTemplate() {
            return (TemplateResource) comboBox.getSelectedItem();
        }
    }
}
