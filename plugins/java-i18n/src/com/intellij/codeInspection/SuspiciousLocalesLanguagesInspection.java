/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.customizeActions.DissociateResourceBundleAction;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.reference.SoftLazyValue;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class SuspiciousLocalesLanguagesInspection extends BaseLocalInspectionTool {
  private static final String ADDITIONAL_LANGUAGES_ATTR_NAME = "additionalLanguages";
  private final static SoftLazyValue<Set<String>> JAVA_LOCALES = new SoftLazyValue<Set<String>>() {
    @NotNull
    @Override
    protected Set<String> compute() {
      final Set<String> result = new HashSet<>();
      for (Locale locale : Locale.getAvailableLocales()) {
        result.add(locale.getLanguage());
      }
      return result;
    }
  };

  private final List<String> myAdditionalLanguages = new ArrayList<>();

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Suspicious resource bundle locale languages";
  }

  @TestOnly
  public void setAdditionalLanguages(List<String> additionalLanguages) {
    myAdditionalLanguages.clear();
    myAdditionalLanguages.addAll(additionalLanguages);
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    final String rawLanguages = node.getAttributeValue(ADDITIONAL_LANGUAGES_ATTR_NAME);
    if (rawLanguages != null) {
      myAdditionalLanguages.clear();
      myAdditionalLanguages.addAll(StringUtil.split(rawLanguages, ","));
    }
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (!myAdditionalLanguages.isEmpty()) {
      final ArrayList<String> uniqueLanguages = ContainerUtil.newArrayList(ContainerUtil.newHashSet(myAdditionalLanguages));
      Collections.sort(uniqueLanguages);
      final String locales = StringUtil.join(uniqueLanguages, ",");
      node.setAttribute(ADDITIONAL_LANGUAGES_ATTR_NAME, locales);
    }
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new MyOptions().getComponent();
  }

  @Nullable
  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
    if (propertiesFile == null) {
      return null;
    }
    final ResourceBundle resourceBundle = propertiesFile.getResourceBundle();
    final List<PropertiesFile> files = resourceBundle.getPropertiesFiles();
    if (!(resourceBundle instanceof ResourceBundleImpl) || files.size() < 2) {
      return null;
    }
    List<Locale> bundleLocales = ContainerUtil.mapNotNull(files, propertiesFile1 -> {
      final Locale locale = propertiesFile1.getLocale();
      return locale == PropertiesUtil.DEFAULT_LOCALE ? null : locale;
    });
    bundleLocales = ContainerUtil.filter(bundleLocales, locale -> !JAVA_LOCALES.getValue().contains(locale.getLanguage()) && !myAdditionalLanguages.contains(locale.getLanguage()));
    if (bundleLocales.isEmpty()) {
      return null;
    }
    final ProblemDescriptor descriptor = manager.createProblemDescriptor(file,
                                                                         PropertiesBundle.message(
                                                                           "resource.bundle.contains.locales.with.suspicious.locale.languages.desciptor"),
                                                                         new DissociateResourceBundleQuickFix(resourceBundle),
                                                                         ProblemHighlightType.WEAK_WARNING,
                                                                         true);
    return new ProblemDescriptor[] {descriptor};
  }

  private static class DissociateResourceBundleQuickFix implements LocalQuickFix {
    private final ResourceBundle myResourceBundle;

    private DissociateResourceBundleQuickFix(ResourceBundle bundle) {
      myResourceBundle = bundle;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return PropertiesBundle.message("dissociate.resource.bundle.quick.fix.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      DissociateResourceBundleAction.dissociate(Collections.singleton(myResourceBundle), project);
    }
  }

  private class MyOptions {
    private final JBList myAdditionalLocalesList;

    public MyOptions() {
      myAdditionalLocalesList = new JBList(new MyListModel());
      myAdditionalLocalesList.setCellRenderer(new DefaultListCellRenderer());
    }

    public JPanel getComponent() {
      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(new JLabel(PropertiesBundle.message("dissociate.resource.bundle.quick.fix.options.label")), BorderLayout.NORTH);
      panel.add(
        ToolbarDecorator.createDecorator(myAdditionalLocalesList)
          .setAddAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton button) {
              Messages.showInputDialog(panel, PropertiesBundle.message("dissociate.resource.bundle.quick.fix.options.input.text"),
                                       PropertiesBundle.message("dissociate.resource.bundle.quick.fix.options.input.title"), null, "", new InputValidator() {
                @Override
                public boolean checkInput(String inputString) {
                  return 1 < inputString.length() && inputString.length() < 9 && !myAdditionalLanguages.contains(inputString);
                }

                @Override
                public boolean canClose(String inputString) {
                  if (inputString != null) {
                    myAdditionalLanguages.add(inputString);
                    ((MyListModel)myAdditionalLocalesList.getModel()).fireContentsChanged();
                  }
                  return true;
                }
              });
            }
          })
          .setRemoveAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton button) {
              final int index = myAdditionalLocalesList.getSelectedIndex();
              if (index > -1 && index < myAdditionalLanguages.size()) {
                myAdditionalLanguages.remove(index);
                ((MyListModel)myAdditionalLocalesList.getModel()).fireContentsChanged();
              }
            }
          })
          .setPreferredSize(new Dimension(-1, 100))
          .disableUpDownActions()
          .createPanel(),
        BorderLayout.CENTER);
      return panel;
    }

    private class MyListModel extends AbstractListModel {
      @Override
      public int getSize() {
        return myAdditionalLanguages.size();
      }

      @Override
      public Object getElementAt(int index) {
        return myAdditionalLanguages.get(index);
      }

      public void fireContentsChanged() {
        fireContentsChanged(myAdditionalLanguages, -1, -1);
      }
    }
  }
}
