// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInspection.*;
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
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.NameUtilCore;
import com.intellij.util.text.UniqueNameGenerator;
import com.intellij.util.ui.ItemRemovable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.generate.UastCodeGenerationPlugin;
import org.jetbrains.uast.generate.UastElementFactory;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
    Map<String, ReplacementBean> keyValuePairs = new LinkedHashMap<>();
    UniqueNameGenerator uniqueNameGenerator = new UniqueNameGenerator();
    for (CommonProblemDescriptor descriptor : descriptors) {
      PsiElement psiElement = ((ProblemDescriptor)descriptor).getPsiElement();
      ULiteralExpression literalExpression = UastUtils.findContaining(psiElement, ULiteralExpression.class);
      if (literalExpression != null) {
        PsiPolyadicExpression concatenation = I18nizeConcatenationQuickFix.getEnclosingLiteralConcatenation(psiElement);
        if (concatenation == null) {
          Object val = literalExpression.getValue();
          if (distinct.add(psiElement) && val instanceof String) {
            String value = StringUtil.escapeStringCharacters((String)val);
            ReplacementBean bean = keyValuePairs.get(value);
            if (bean != null) {
              bean.getPsiElements().add(psiElement);
              bean.getExpressions().add(literalExpression);
            }
            else {
              String key = ObjectUtils.notNull(suggestKeyByPlace(literalExpression),
                                               I18nizeQuickFixDialog.suggestUniquePropertyKey(value, null, null));
              ArrayList<PsiElement> elements = new ArrayList<>();
              elements.add(psiElement);
              List<UExpression> uExpressions = new ArrayList<>();
              uExpressions.add(literalExpression);
              keyValuePairs.put(value, new ReplacementBean(uniqueNameGenerator.generateUniqueName(key), value, uExpressions, elements, Collections.emptyList()));
            }
          }
        }
        else if (distinct.add(concatenation)) {
          ArrayList<PsiExpression> args = new ArrayList<>();
          String value = I18nizeConcatenationQuickFix.getValueString(concatenation, args);
          String key = ObjectUtils.notNull(suggestKeyByPlace(literalExpression),
                                           I18nizeQuickFixDialog.suggestUniquePropertyKey(value, null, null));
          keyValuePairs.put(value + concatenation.hashCode(),
                            new ReplacementBean(uniqueNameGenerator.generateUniqueName(key),
                                                value,
                                                Collections.singletonList(UastUtils.findContaining(concatenation, UPolyadicExpression.class)),
                                                Collections.singletonList(concatenation),
                                                ContainerUtil.map(args, arg -> UastUtils.findContaining(arg, UExpression.class))));
        }
      }
    }

    if (keyValuePairs.isEmpty()) return;

    ArrayList<ReplacementBean> replacements = new ArrayList<>(keyValuePairs.values());
    I18NBatchDialog dialog = new I18NBatchDialog(project, replacements);
    if (dialog.showAndGet()) {
      PropertiesFile propertiesFile = dialog.getPropertiesFile();
      Set<PsiFile> files = new HashSet<>();
      for (ReplacementBean pair : replacements) {
        for (PsiElement element : pair.getPsiElements()) {
          ContainerUtil.addIfNotNull(files, element.getContainingFile());
        }
      }
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
        for (ReplacementBean bean : replacements) {
          JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER.createProperty(project,
                                                                        Collections.singletonList(propertiesFile),
                                                                        bean.getKey(),
                                                                        bean.getValue(),
                                                                        PsiExpression.EMPTY_ARRAY);
          List<UExpression> uExpressions = bean.getExpressions();
          List<PsiElement> psiElements = bean.getPsiElements();
          for (int i = 0; i < psiElements.size(); i++) {
            PsiElement psiElement = psiElements.get(i);
            UExpression uExpression = uExpressions.get(i);
            Language language = psiElement.getLanguage();
            UastCodeGenerationPlugin generationPlugin = UastCodeGenerationPlugin.byLanguage(language);
            if (generationPlugin == null) {
              LOG.debug("No UAST generation plugin exist for " + language.getDisplayName());
              continue;
            }
            UastElementFactory pluginElementFactory = generationPlugin.getElementFactory(project);
            List<UExpression> arguments = new ArrayList<>();
            arguments.add(pluginElementFactory.createStringLiteralExpression(bean.getKey(), psiElement));
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
        }
      }, files.toArray(PsiFile.EMPTY_ARRAY));
    }
  }

  /**
   * If expression is passed to ProblemsHolder#registerProblem, suggest inspection.class.name.description key
   * If expression is returned from getName/getFamilyName of the LocalQuickFix, suggest quick.fix.text/family.name
   */
  @Nullable
  private static String suggestKeyByPlace(@NotNull UExpression expression) {
    UElement parent = UastUtils.skipParenthesizedExprUp(expression.getUastParent());
    if (parent instanceof UPolyadicExpression) {
      parent = UastUtils.skipParenthesizedExprUp(parent.getUastParent());
    }
    if (parent == null) return null;
    UCallExpression callExpression = UastUtils.getUCallExpression(parent);
    if (callExpression != null) {
      PsiMethod method = callExpression.resolve();
      if (method != null) {
        if ("registerProblem".equals(method.getName()) &&
            InheritanceUtil.isInheritor(method.getContainingClass(), ProblemsHolder.class.getName())) {
          PsiClass containingClass = PsiTreeUtil.getParentOfType(callExpression.getSourcePsi(), PsiClass.class);
          while (containingClass != null) {
            if (InheritanceUtil.isInheritor(containingClass, InspectionProfileEntry.class.getName())) {
              String containingClassName = containingClass.getName();
              return containingClassName == null
                     ? null
                     : "inspection." + toPropertyName(InspectionProfileEntry.getShortName(containingClassName)) + ".description";
            }
            containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class, true);
          }
        }
      }
      return null;
    }

    final UElement returnStmt =
      UastUtils.getParentOfType(parent, UReturnExpression.class, false, UCallExpression.class, ULambdaExpression.class);
    if (returnStmt instanceof UReturnExpression) {
      UMethod uMethod = UastUtils.getParentOfType(expression, UMethod.class);
      if (uMethod != null) {
        UElement uClass = uMethod.getUastParent();
        if (uClass instanceof UClass && InheritanceUtil.isInheritor(((UClass)uClass), LocalQuickFix.class.getName())) {
          String name = ((UClass)uClass).getName();
          if (name != null) {
            if ("getName".equals(uMethod.getName())) {
              return toPropertyName(name) + ".text";
            }
            if ("getFamilyName".equals(uMethod.getName())) {
              return toPropertyName(name) + ".family.name";
            }
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static String toPropertyName(String name) {
    return StringUtil.join(NameUtilCore.splitNameIntoWords(name), s -> StringUtil.decapitalize(s), ".");
  }

  private static class I18NBatchDialog extends DialogWrapper {
    private static final @NonNls String LAST_USED_PROPERTIES_FILE = "LAST_USED_PROPERTIES_FILE";

    @NotNull private final Project myProject;
    private final List<ReplacementBean> myKeyValuePairs;
    private JComboBox<String> myPropertiesFile;
    private UsagePreviewPanel myUsagePreviewPanel;

    protected I18NBatchDialog(@NotNull Project project, List<ReplacementBean> keyValuePairs) {
      super(project, true);
      myProject = project;
      myKeyValuePairs = keyValuePairs;
      setTitle(CodeInsightBundle.message("i18nize.dialog.title"));
      init();
    }

    @Override
    protected @Nullable String getDimensionServiceKey() {
      return "i18nInBatch";
    }

    @Nullable
    @Override
    protected JComponent createNorthPanel() {
      List<String> files = I18nUtil.defaultSuggestPropertiesFiles(myProject);
      myPropertiesFile = new ComboBox<>(ArrayUtil.toStringArray(files));
      new ComboboxSpeedSearch(myPropertiesFile);
      LabeledComponent<JComboBox<String>> component = new LabeledComponent<>();
      component.setText("Property file:");
      component.setComponent(myPropertiesFile);
      myPropertiesFile.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          PropertiesFile propertiesFile = getPropertiesFile();
          if (propertiesFile != null) {
            for (int i = 0; i < myKeyValuePairs.size(); i++) {
              ReplacementBean keyValuePair = myKeyValuePairs.get(i);
              ReplacementBean updated =
                new ReplacementBean(I18nizeQuickFixDialog.suggestUniquePropertyKey(keyValuePair.myValue, keyValuePair.myKey, propertiesFile),
                                    keyValuePair.myValue,
                                    keyValuePair.myExpressions,
                                    keyValuePair.myPsiElements,
                                    keyValuePair.myArgs);
              myKeyValuePairs.set(i, updated);
            }
          }
        }
      });

      if (!files.isEmpty()) {
        myPropertiesFile.setSelectedItem(ObjectUtils.notNull(PropertiesComponent.getInstance(myProject).getValue(LAST_USED_PROPERTIES_FILE),
                                                             files.get(0)));
      }
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
      Splitter splitter = new JBSplitter(true);
      myUsagePreviewPanel = new UsagePreviewPanel(myProject, new UsageViewPresentation());
      JBTable table = new JBTable(new MyKeyValueModel());
      table.getSelectionModel().addListSelectionListener(e -> {
        int index = table.getSelectionModel().getLeadSelectionIndex();
        if (index != -1) {
          List<PsiElement> elements = myKeyValuePairs.get(index).getPsiElements();
          myUsagePreviewPanel.updateLayout(ContainerUtil.map(elements, element -> new UsageInfo(element.getParent())));
        }
        else {
          myUsagePreviewPanel.updateLayout(null);
        }
      });

      splitter.setFirstComponent(ToolbarDecorator.createDecorator(table).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          TableUtil.removeSelectedItems(table);
          table.repaint();
        }
      }).createPanel());
      splitter.setSecondComponent(myUsagePreviewPanel);
      return splitter;
    }

    @Override
    protected void doOKAction() {
      PropertiesComponent.getInstance(myProject).setValue(LAST_USED_PROPERTIES_FILE, (String)myPropertiesFile.getSelectedItem());
      super.doOKAction();
    }

    @Override
    protected void dispose() {
      Disposer.dispose(myUsagePreviewPanel);
      super.dispose();
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
          myKeyValuePairs.set(rowIndex, new ReplacementBean((String)aValue, bean.getValue(), bean.getExpressions(), bean.getPsiElements(), bean.getArgs()));
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
    private final List<UExpression> myExpressions;
    private final List<PsiElement> myPsiElements;
    private final List<UExpression> myArgs;

    private ReplacementBean(String key,
                            String value,
                            List<UExpression> expression,
                            List<PsiElement> psiElements,
                            List<UExpression> args) {
      myKey = key;
      myValue = value;
      myExpressions = expression;
      myPsiElements = psiElements;
      myArgs = args;
    }

    public String getKey() {
      return myKey;
    }

    private String getValue() {
      return myValue;
    }

    private List<UExpression> getExpressions() {
      return myExpressions;
    }

    private List<UExpression> getArgs() {
      return myArgs;
    }

    private List<PsiElement> getPsiElements() {
      return myPsiElements;
    }
  }
}
