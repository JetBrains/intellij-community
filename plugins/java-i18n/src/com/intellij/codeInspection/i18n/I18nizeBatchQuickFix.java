// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInspection.BatchQuickFix;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.references.I18nUtil;
import com.intellij.lang.properties.references.I18nizeQuickFixDialog;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ItemRemovable;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class I18nizeBatchQuickFix extends I18nizeQuickFix implements BatchQuickFix<CommonProblemDescriptor> {
  private static final Logger LOG = Logger.getInstance(I18nizeBatchQuickFix.class);


  @Override
  public void applyFix(@NotNull Project project,
                       CommonProblemDescriptor @NotNull [] descriptors,
                       @NotNull List<PsiElement> psiElementsToIgnore,
                       @Nullable Runnable refreshViews) {
    List<ReplacementBean> keyValuePairs = ContainerUtil.mapNotNull(descriptors, descriptor -> {
      PsiElement psiElement = ((ProblemDescriptor)descriptor).getPsiElement();
      if (psiElement instanceof PsiLiteralExpression &&
          I18nizeConcatenationQuickFix.getEnclosingLiteralConcatenation(psiElement) == null) {
        Object val = ((PsiLiteralExpression)psiElement).getValue();
        if (val instanceof String) {
          String value = StringUtil.escapeStringCharacters((String)val);
          String key = I18nizeQuickFixDialog.suggestUniquePropertyKey(value, null, null);
          return new ReplacementBean(key, value, (PsiLiteralExpression)psiElement);
        }
      }
      return null;
    });

    if (keyValuePairs.isEmpty()) return;

    I18NBatchDialog dialog = new I18NBatchDialog(project, keyValuePairs);
    if (dialog.showAndGet()) {
      PropertiesFile propertiesFile = dialog.getPropertiesFile();
      PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      FileTemplate template = FileTemplateManager.getInstance(project).getCodeTemplate(JavaTemplateUtil.TEMPLATE_I18NIZED_EXPRESSION);
      List<PsiFile> files = ContainerUtil.mapNotNull(keyValuePairs, bean -> bean.getExpression().getContainingFile());
      if (files.isEmpty()) {
        return;
      }
      files.add(propertiesFile.getContainingFile());

      WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null, () -> {
        String bundleName = propertiesFile.getVirtualFile().getNameWithoutExtension();
        for (ReplacementBean bean : keyValuePairs) {
          JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER
            .createProperty(project, Collections.singletonList(propertiesFile), bean.getKey(), bean.getValue(), PsiExpression.EMPTY_ARRAY);
          PsiElement literalExpression = bean.getExpression();
          String i18NText = getI18NText(bean.getKey(), bean.getValue(), bundleName, template);
          PsiExpression expression = factory.createExpressionFromText(i18NText, literalExpression);
          literalExpression.replace(expression);
        }
      }, files.toArray(PsiFile.EMPTY_ARRAY));
    }
  }

  private static String getI18NText(String key, String value, String bundleName, FileTemplate template) {
    Map<String, String> attributes = new THashMap<>();
    attributes.put(JavaI18nizeQuickFixDialog.PROPERTY_KEY_OPTION_KEY, StringUtil.escapeStringCharacters(key));
    attributes.put(JavaI18nizeQuickFixDialog.RESOURCE_BUNDLE_OPTION_KEY, bundleName);
    attributes.put(JavaI18nizeQuickFixDialog.PROPERTY_VALUE_ATTR, StringUtil.escapeStringCharacters(value));

    String text = null;
    try {
      text = template.getText(attributes);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return text;
  }

  private static class I18NBatchDialog extends DialogWrapper {
    private static final @NonNls String LAST_USED_PROPERTIES_FILE = "LAST_USED_PROPERTIES_FILE";

    @NotNull private final Project myProject;
    private final List<ReplacementBean> myKeyValuePairs;
    private JComboBox<String> myPropertiesFile;

    protected I18NBatchDialog(@NotNull Project project, List<ReplacementBean> keyValuePairs) {
      super(project, true);
      myProject = project;
      myKeyValuePairs = keyValuePairs;
      setTitle(CodeInsightBundle.message("i18nize.dialog.title"));
      init();
    }

    @Nullable
    @Override
    protected JComponent createNorthPanel() {
      List<String> files = I18nUtil.defaultSuggestPropertiesFiles(myProject);
      myPropertiesFile = new ComboBox<>(ArrayUtil.toStringArray(files));
      new ComboboxSpeedSearch(myPropertiesFile);
      if (!files.isEmpty()) {
        myPropertiesFile.setSelectedItem(ObjectUtils.notNull(PropertiesComponent.getInstance(myProject).getValue(LAST_USED_PROPERTIES_FILE),
                                                             files.get(0)));
      }
      LabeledComponent<JComboBox<String>> component = new LabeledComponent<>();
      component.setText("Property file:");
      component.setComponent(myPropertiesFile);

      return component;
    }

    protected PropertiesFile getPropertiesFile() {
      Object selectedItem = myPropertiesFile.getSelectedItem();
      if (selectedItem == null) return null;
      String path = FileUtil.toSystemIndependentName((String)selectedItem);
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
      return virtualFile != null
             ? PropertiesImplUtil.getPropertiesFile(PsiManager.getInstance(myProject).findFile(virtualFile))
             : null;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JBTable table = new JBTable(new MyKeyValueModel());
      return ToolbarDecorator.createDecorator(table).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          TableUtil.removeSelectedItems(table);
          table.repaint();
        }
      }).createPanel();
    }

    @Override
    protected void doOKAction() {
      PropertiesComponent.getInstance(myProject).setValue(LAST_USED_PROPERTIES_FILE, (String)myPropertiesFile.getSelectedItem());
      super.doOKAction();
    }

    private class MyKeyValueModel extends AbstractTableModel implements ItemRemovable {
      @Override
      public int getRowCount() {
        return myKeyValuePairs.size();
      }

      @Override
      public String getColumnName(int column) {
        return column == 0 ? "Key" : "Value";
      }

      @Override
      public int getColumnCount() {
        return 2;
      }

      @Override
      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 0;
      }

      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {
        ReplacementBean pair = myKeyValuePairs.get(rowIndex);
        return columnIndex == 0 ? pair.getKey() : pair.getValue();
      }

      @Override
      public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == 0) {
          ReplacementBean bean = myKeyValuePairs.get(rowIndex);
          myKeyValuePairs.set(rowIndex, new ReplacementBean((String)aValue, bean.getValue(), bean.getExpression()));
        }
      }

      @Override
      public void removeRow(int idx) {
        myKeyValuePairs.remove(idx);
      }
    }
  }

  private static class ReplacementBean {
    private final String myKey;
    private final String myValue;
    private final PsiLiteralExpression myExpression;

    private ReplacementBean(String key, String value, PsiLiteralExpression expression) {
      myKey = key;
      myValue = value;
      myExpression = expression;
    }

    public String getKey() {
      return myKey;
    }

    private String getValue() {
      return myValue;
    }

    private PsiLiteralExpression getExpression() {
      return myExpression;
    }
  }
}
