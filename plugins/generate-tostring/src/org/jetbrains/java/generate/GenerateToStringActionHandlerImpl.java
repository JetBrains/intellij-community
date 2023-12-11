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
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.generation.PsiElementClassMember;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.ide.util.MemberChooser;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.TabbedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.generate.tostring.GenerateToStringClassFilter;
import org.jetbrains.java.generate.config.Config;
import org.jetbrains.java.generate.config.ConflictResolutionPolicy;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.template.toString.ToStringTemplatesManager;
import org.jetbrains.java.generate.view.TemplatesPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;

/**
 * The action-handler that does the code generation.
 */
public class GenerateToStringActionHandlerImpl implements GenerateToStringActionHandler, CodeInsightActionHandler {
    private static final Logger LOG = Logger.getInstance(GenerateToStringActionHandlerImpl.class);

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        PsiClass clazz = getSubjectClass(editor, file);
        if (clazz == null) return;

        doExecuteAction(project, clazz, editor);
    }


    @Override
    public void executeActionQuickFix(final Project project, final PsiClass clazz) {
        doExecuteAction(project, clazz, null);
    }

    private static void doExecuteAction(@NotNull final Project project, @NotNull final PsiClass clazz, final Editor editor) {
        if (!FileModificationService.getInstance().preparePsiElementsForWrite(clazz)) {
            return;
        }
        LOG.debug("+++ doExecuteAction - START +++");

        if (LOG.isDebugEnabled()) {
            LOG.debug("Current project " + project.getName());
        }

        final PsiElementClassMember<?>[] dialogMembers = buildMembersToShow(clazz);

        final MemberChooserHeaderPanel header = new MemberChooserHeaderPanel(clazz);
        LOG.debug("Displaying member chooser dialog");

        final MemberChooser<PsiElementClassMember<?>> chooser =
          new MemberChooser<>(dialogMembers, true, true, project, PsiUtil.isLanguageLevel5OrHigher(clazz), header) {
            @Override
            protected @NotNull String getHelpId() {
              return "editing.altInsert.tostring";
            }

            @Override
            protected boolean isInsertOverrideAnnotationSelected() {
              return JavaCodeStyleSettings.getInstance(clazz.getContainingFile()).INSERT_OVERRIDE_ANNOTATION;
            }
          };
        //noinspection DialogTitleCapitalization
        chooser.setTitle(JavaBundle.message("generate.tostring.title"));

        chooser.setCopyJavadocVisible(false);
        chooser.selectElements(getPreselection(clazz, dialogMembers));
        header.setChooser(chooser);

        if (ApplicationManager.getApplication().isUnitTestMode()) {
          chooser.close(DialogWrapper.OK_EXIT_CODE);
        }
        else {
          chooser.show();
        }
        if (DialogWrapper.OK_EXIT_CODE == chooser.getExitCode()) {
            Collection<PsiMember> selectedMembers = GenerationUtil.convertClassMembersToPsiMembers(sortedSelection(dialogMembers, chooser));

            final TemplateResource template = header.getSelectedTemplate();
            ToStringTemplatesManager.getInstance().setDefaultTemplate(template);

            if (template.isValidTemplate()) {
                final GenerateToStringWorker worker = new GenerateToStringWorker(clazz, editor, chooser.isInsertOverrideAnnotation());
                // decide what to do if the method already exists
                ConflictResolutionPolicy resolutionPolicy = worker.exitsMethodDialog(template);
                try {
                    WriteCommandAction.runWriteCommandAction(project, JavaBundle.message("command.name.generate.tostring"), null,
                                                             () -> worker.execute(selectedMembers, template, resolutionPolicy));
                }
                catch (Exception e) {
                    GenerationUtil.handleException(project, e);
                }
            }
            else {
                HintManager.getInstance().showErrorHint(editor,
                                                        JavaBundle.message("hint.text.tostring.template.invalid", template.getFileName()));
            }
        }

        LOG.debug("+++ doExecuteAction - END +++");
    }

  @Nullable
  private static List<PsiElementClassMember<?>> sortedSelection(PsiElementClassMember<?>[] dialogMembers,
                                                                MemberChooser<PsiElementClassMember<?>> chooser) {
    List<PsiElementClassMember<?>> selectedElements = chooser.getSelectedElements();
    if (selectedElements == null) {
      return null;
    }
    return GenerateToStringContext.getConfig().getSortElements() == 0
           ? selectedElements
           : ContainerUtil.filter(dialogMembers, selectedElements::contains);
  }

  private static PsiElementClassMember<?>[] getPreselection(@NotNull PsiClass clazz, PsiElementClassMember<?>[] dialogMembers) {
        return Arrays.stream(dialogMembers)
          .filter(member -> member.getElement().getContainingClass() == clazz)
          .toArray(PsiElementClassMember[]::new);
    }

    public static void updateDialog(PsiClass clazz, MemberChooser<? super PsiElementClassMember<?>> dialog) {
        final PsiElementClassMember<?>[] members = buildMembersToShow(clazz);
        dialog.resetElements(members);
        dialog.selectElements(getPreselection(clazz, members));
    }

    public static PsiElementClassMember<?>[] buildMembersToShow(PsiClass clazz) {
        Config config = GenerateToStringContext.getConfig();
        PsiField[] filteredFields = GenerateToStringUtils.filterAvailableFields(clazz, true, config.getFilterPattern());
        if (LOG.isDebugEnabled()) LOG.debug("Number of fields after filtering: " + filteredFields.length);
        PsiMethod[] filteredMethods;
        if (config.enableMethods) {
            // filter methods as it is enabled from config
            filteredMethods = GenerateToStringUtils.filterAvailableMethods(clazz, config.getFilterPattern());
            if (LOG.isDebugEnabled()) LOG.debug("Number of methods after filtering: " + filteredMethods.length);
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
        for (GenerateToStringClassFilter filter : GenerateToStringClassFilter.EP_NAME.getExtensionList()) {
            if (!filter.canGenerateToString(clazz)) return null;
        }
        return clazz;
    }

    public static class MemberChooserHeaderPanel extends JPanel {
        private MemberChooser<PsiElementClassMember<?>> chooser;
        private final JComboBox<TemplateResource> comboBox;

        public void setChooser(MemberChooser<PsiElementClassMember<?>> chooser) {
            this.chooser = chooser;
        }

        public MemberChooserHeaderPanel(final PsiClass clazz) {
            super(new GridBagLayout());

            final Collection<TemplateResource> templates = ToStringTemplatesManager.getInstance().getAllTemplates();
            final TemplateResource[] all = templates.toArray(new TemplateResource[0]);

            final JButton settingsButton = new JButton(JavaBundle.message("button.text.settings"));
            settingsButton.setMnemonic(KeyEvent.VK_S);

          comboBox = new ComboBox<>(all);
          final Set<String> inaccessibleTemplates = new HashSet<>();
          final JavaPsiFacade instance = JavaPsiFacade.getInstance(clazz.getProject());
          final GlobalSearchScope resolveScope = clazz.getResolveScope();
          ReadAction.nonBlocking(() -> {
              for (TemplateResource template : templates) {
                String className = template.getClassName();
                if (className != null && instance.findClass(className, resolveScope) == null) {
                  inaccessibleTemplates.add(className);
                }
              }
              return inaccessibleTemplates;
            })
            .finishOnUiThread(ModalityState.current(), ts -> {
              if (!ts.isEmpty()) SwingUtilities.invokeLater(comboBox::repaint);
            })
            .submit(AppExecutorUtil.getAppExecutorService());;
          final ListCellRenderer<TemplateResource> renderer =
            SimpleListCellRenderer.create((label, value, index) -> {
              label.setText(value.getName());
              final String className = value.getClassName();
              if (className != null && inaccessibleTemplates.contains(className)) {
                label.setForeground(JBColor.RED);
              }
            });
            comboBox.setRenderer(renderer);
            settingsButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                  final TemplatesPanel ui = new TemplatesPanel(clazz.getProject());
                  Configurable composite = new TabbedConfigurable() {
                        @Override
                        @NotNull
                        protected List<Configurable> createConfigurables() {
                            List<Configurable> res = new ArrayList<>();
                            res.add(new GenerateToStringConfigurable(clazz.getProject()));
                            res.add(ui);
                            return res;
                        }

                        @Override
                        public String getDisplayName() {
                            return JavaBundle.message("generate.tostring.tab.title");
                        }

                        @Override
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
                  composite.disposeUIResources();
                }
            });

            comboBox.setSelectedItem(ToStringTemplatesManager.getInstance().getDefaultTemplate());

            final JLabel templatesLabel = new JLabel(JavaBundle.message("generate.tostring.template.label"));
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
