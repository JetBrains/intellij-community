// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInspection.BatchQuickFix;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
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
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ItemRemovable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.generate.UastCodeGenerationPlugin;
import org.jetbrains.uast.generate.UastElementFactory;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.*;

/**
 * WARNING
 * Templates are ignored BundleName.message(key, args) is always used instead
 */
public class I18nizeBatchQuickFix extends I18nizeQuickFix implements BatchQuickFix<CommonProblemDescriptor> {
  private static final Logger LOG = Logger.getInstance(I18nizeBatchQuickFix.class);


  @Override
  public void applyFix(@NotNull Project project,
                       CommonProblemDescriptor @NotNull [] descriptors,
                       @NotNull List<PsiElement> psiElementsToIgnore,
                       @Nullable Runnable refreshViews) {
    Set<PsiElement> distinct = new HashSet<>();
    List<ReplacementBean> keyValuePairs = ContainerUtil.mapNotNull(descriptors, descriptor -> {
      PsiElement psiElement = ((ProblemDescriptor)descriptor).getPsiElement();
      ULiteralExpression literalExpression = UastUtils.findContaining(psiElement, ULiteralExpression.class);
      if (literalExpression != null) {
        PsiPolyadicExpression concatenation = I18nizeConcatenationQuickFix.getEnclosingLiteralConcatenation(psiElement);
        if (concatenation == null) {
          Object val = literalExpression.getValue();
          if (distinct.add(psiElement) && val instanceof String) {
            String value = StringUtil.escapeStringCharacters((String)val);
            String key = I18nizeQuickFixDialog.suggestUniquePropertyKey(value, null, null);
            return new ReplacementBean(key, value, literalExpression, psiElement, Collections.emptyList());
          }
        }
        else if (distinct.add(concatenation)) {
          ArrayList<PsiExpression> args = new ArrayList<>();
          String value = I18nizeConcatenationQuickFix.getValueString(concatenation, args);
          String key = I18nizeQuickFixDialog.suggestUniquePropertyKey(value, null, null);
          return new ReplacementBean(key, 
                                     value, 
                                     UastUtils.findContaining(concatenation, UPolyadicExpression.class), 
                                     concatenation, 
                                     ContainerUtil.map(args, arg -> UastUtils.findContaining(arg, UExpression.class) ));
        }
      }
      return null;
    });

    if (keyValuePairs.isEmpty()) return;

    I18NBatchDialog dialog = new I18NBatchDialog(project, keyValuePairs);
    if (dialog.showAndGet()) {
      PropertiesFile propertiesFile = dialog.getPropertiesFile();
      List<PsiFile> files = ContainerUtil.mapNotNull(keyValuePairs, bean -> bean.getPsiElement().getContainingFile());
      if (files.isEmpty()) {
        return;
      }
      files.add(propertiesFile.getContainingFile());

      WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null, () -> {
        String bundleName = propertiesFile.getVirtualFile().getNameWithoutExtension();
        PsiClass[] classesByName = PsiShortNamesCache.getInstance(project).getClassesByName(bundleName, 
                                                                                            GlobalSearchScope.projectScope(project));
        if (classesByName.length == 1) {
          bundleName = classesByName[0].getQualifiedName();
          LOG.assertTrue(bundleName != null, propertiesFile.getName());
        }
        for (ReplacementBean bean : keyValuePairs) {
          JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER.createProperty(project,
                                                                        Collections.singletonList(propertiesFile),
                                                                        bean.getKey(),
                                                                        bean.getValue(),
                                                                        PsiExpression.EMPTY_ARRAY);
          UExpression uExpression = bean.getExpression();
          PsiElement psiElement = bean.getPsiElement();
          Language language = psiElement.getLanguage();
          UastCodeGenerationPlugin generationPlugin = UastCodeGenerationPlugin.byLanguage(language);
          if (generationPlugin == null) {
            LOG.debug("No UAST generation plugin exist for " + language.getDisplayName());
            continue;
          }
          UastElementFactory pluginElementFactory = generationPlugin.getElementFactory(project);
          List<UExpression> arguments = new ArrayList<>();
          arguments.add(pluginElementFactory.createULiteralExpression("\"" + bean.getKey() + "\"", psiElement));
          arguments.addAll(bean.getArgs());
          UCallExpression callExpression = pluginElementFactory
            .createCallExpression(pluginElementFactory.createQualifiedReference(bundleName, uExpression),
                                  "message",
                                  arguments,
                                  null,
                                  UastCallKind.METHOD_CALL,
                                  psiElement);
          if (callExpression != null) {
            generationPlugin.replace(uExpression, callExpression, UCallExpression.class);
          }
          else {
            LOG.debug("Null generated UAST call expression");
          }
        }
      }, files.toArray(PsiFile.EMPTY_ARRAY));
    }
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
          myKeyValuePairs.set(rowIndex, new ReplacementBean((String)aValue, bean.getValue(), bean.getExpression(), bean.getPsiElement(), bean.getArgs()));
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
    private final UExpression myExpression;
    private final PsiElement myPsiElement;
    private final List<UExpression> myArgs;

    private ReplacementBean(String key,
                            String value,
                            UExpression expression,
                            PsiElement psiElement,
                            List<UExpression> args) {
      myKey = key;
      myValue = value;
      myExpression = expression;
      myPsiElement = psiElement;
      myArgs = args;
    }

    public String getKey() {
      return myKey;
    }

    private String getValue() {
      return myValue;
    }

    private UExpression getExpression() {
      return myExpression;
    }

    private List<UExpression> getArgs() {
      return myArgs;
    }

    private PsiElement getPsiElement() {
      return myPsiElement;
    }
  }
}
