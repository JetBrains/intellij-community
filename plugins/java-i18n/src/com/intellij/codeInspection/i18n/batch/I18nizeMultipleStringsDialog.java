// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n.batch;

import com.intellij.codeInspection.i18n.I18nizeConcatenationQuickFix;
import com.intellij.codeInspection.i18n.JavaI18nizeQuickFixDialog;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.psi.I18nizedTextGenerator;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.ResourceBundleManager;
import com.intellij.lang.properties.references.I18nUtil;
import com.intellij.lang.properties.references.I18nizeQuickFixDialog;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.ui.UI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class I18nizeMultipleStringsDialog<D> extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(I18nizeMultipleStringsDialog.class);
  private static final @NonNls String LAST_USED_PROPERTIES_FILE = "LAST_USED_PROPERTIES_FILE";
  private static final @NonNls String LAST_USED_CONTEXT = "I18N_FIX_LAST_USED_CONTEXT";

  @NotNull private final Project myProject;
  private final List<I18nizedPropertyData<D>> myKeyValuePairs;
  private final @NotNull Function<? super D, ? extends List<UsageInfo>> myUsagePreviewProvider;
  private final Set<Module> myContextModules;
  private final ResourceBundleManager myResourceBundleManager;
  private JComboBox<String> myPropertiesFile;
  private UsagePreviewPanel myUsagePreviewPanel;
  private JBTable myTable;
  private final Icon myMarkAsNonNlsButtonIcon;
  private TextFieldWithHistory myRBEditorTextField;
  private boolean myShowCodeInfo;

  public I18nizeMultipleStringsDialog(@NotNull Project project,
                                      @NotNull List<I18nizedPropertyData<D>> keyValuePairs,
                                      @NotNull Set<PsiFile> contextFiles,
                                      @Nullable ResourceBundleManager bundleManager,
                                      @NotNull Function<? super D, ? extends List<UsageInfo>> usagePreviewProvider,
                                      Icon markAsNonNlsButtonIcon,
                                      boolean canShowCodeInfo) {
    super(project, true);
    myProject = project;
    myKeyValuePairs = keyValuePairs;
    myUsagePreviewProvider = usagePreviewProvider;
    myMarkAsNonNlsButtonIcon = markAsNonNlsButtonIcon;
    myContextModules = contextFiles.stream().map(ModuleUtilCore::findModuleForFile).filter(Objects::nonNull).collect(Collectors.toSet());
    myResourceBundleManager = bundleManager;
    if (bundleManager != null) myShowCodeInfo = canShowCodeInfo && myResourceBundleManager.canShowJavaCodeInfo();
    setTitle(PropertiesBundle.message("i18nize.multiple.strings.dialog.title"));
    init();
  }

  public String getI18NText(String propertyKey, String propertyValue, String paramsString) {
    I18nizedTextGenerator textGenerator = myResourceBundleManager.getI18nizedTextGenerator();
    if (textGenerator != null) {
      return textGenerator.getI18nizedConcatenationText(propertyKey, paramsString, getPropertiesFile(), null);
    }

    String templateName = paramsString.isEmpty() ? myResourceBundleManager.getTemplateName()
                                                 : myResourceBundleManager.getConcatenationTemplateName();
    LOG.assertTrue(templateName != null);
    FileTemplate template = FileTemplateManager.getInstance(myProject).getCodeTemplate(templateName);
    Map<String, String> attributes = new HashMap<>();
    attributes.put(JavaI18nizeQuickFixDialog.PROPERTY_KEY_OPTION_KEY, propertyKey);
    attributes.put(JavaI18nizeQuickFixDialog.RESOURCE_BUNDLE_OPTION_KEY, myRBEditorTextField != null ? myRBEditorTextField.getText() : null);
    attributes.put(JavaI18nizeQuickFixDialog.PROPERTY_VALUE_ATTR, propertyValue);
    attributes.put(I18nizeConcatenationQuickFix.PARAMETERS_OPTION_KEY, paramsString);
    try {
      return template.getText(attributes);
    }
    catch (IOException e) {
      LOG.error(e);
      return "";
    }
  }

  @Override
  protected String getDimensionServiceKey() {
    return "i18nInBatch";
  }

  @Override
  protected JComponent createNorthPanel() {
    myPropertiesFile = new ComboBox<>();
    SwingUtilities.invokeLater(() -> {
      ReadAction.nonBlocking(() -> myResourceBundleManager != null ? myResourceBundleManager.suggestPropertiesFiles(myContextModules)
                                                                   : I18nUtil.defaultSuggestPropertiesFiles(myProject, myContextModules))
        .finishOnUiThread(ModalityState.stateForComponent(myPropertiesFile), files -> {
          myPropertiesFile.setModel(new DefaultComboBoxModel<>(ArrayUtil.toStringArray(files)));
          if (!files.isEmpty()) {
            String contextString = getContextString();
            @NlsSafe String preselectedFile;
            if (contextString != null && contextString.equals(PropertiesComponent.getInstance(myProject).getValue(LAST_USED_CONTEXT))) {
              preselectedFile = PropertiesComponent.getInstance(myProject).getValue(LAST_USED_PROPERTIES_FILE);
            }
            else {
              preselectedFile = null;
            }
            myPropertiesFile.setSelectedItem(ObjectUtils.notNull(preselectedFile, files.get(0)));
          }
        })
        .submit(AppExecutorUtil.getAppExecutorService());
    });
    ComboboxSpeedSearch.installOn(myPropertiesFile);
    LabeledComponent<JComboBox<String>> component = new LabeledComponent<>();
    component.setText(JavaI18nBundle.message("property.file"));
    component.setComponent(myPropertiesFile);
    myPropertiesFile.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        PropertiesFile propertiesFile = getPropertiesFile();
        if (propertiesFile != null) {
          List<I18nizedPropertyData<D>> pairs = myKeyValuePairs;
          ReadAction.nonBlocking(() -> {
            List<I18nizedPropertyData<D>> result = new ArrayList<>();
            for (I18nizedPropertyData<D> data : pairs) {
              result.add(data.changeKey(I18nizeQuickFixDialog.suggestUniquePropertyKey(data.value(), data.key(), propertiesFile)));
            }  
            return result;
          }).finishOnUiThread(ModalityState.stateForComponent(myPropertiesFile), datum -> {
            for (int i = 0; i < datum.size(); i++) {
              myKeyValuePairs.set(i, datum.get(i));
            }
          }).submit(AppExecutorUtil.getAppExecutorService());
        }
      }
    });

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(component, BorderLayout.NORTH);

    if (myShowCodeInfo && hasResourceBundleInTemplate()) {
      myRBEditorTextField = new TextFieldWithStoredHistory("RESOURCE_BUNDLE_KEYS");
      if (!myRBEditorTextField.getHistory().isEmpty()) {
        myRBEditorTextField.setSelectedIndex(0);
      }
      panel.add(UI.PanelFactory.panel(myRBEditorTextField)
                  .withLabel(JavaI18nBundle.message("i18n.quickfix.code.panel.resource.bundle.expression.label"))
                  .withComment(JavaI18nBundle.message("comment.if.the.resource.bundle.is.invalid.either.declare.it.as.an.object"))
                  .createPanel(), BorderLayout.SOUTH);
    }

    return panel;
  }

  private boolean hasResourceBundleInTemplate() {
    return JavaI18nizeQuickFixDialog.showResourceBundleTextField(myResourceBundleManager.getTemplateName(), myProject) ||
           JavaI18nizeQuickFixDialog.showResourceBundleTextField(myResourceBundleManager.getConcatenationTemplateName(), myProject);
  }

  public PropertiesFile getPropertiesFile() {
    Object selectedItem = myPropertiesFile.getSelectedItem();
    if (selectedItem == null) return null;
    String path = FileUtil.toSystemIndependentName((String)selectedItem);
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    return virtualFile != null
           ? PropertiesImplUtil.getPropertiesFile(PsiManager.getInstance(myProject).findFile(virtualFile))
           : null;
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    Splitter splitter = new JBSplitter(true);
    myUsagePreviewPanel = new UsagePreviewPanel(myProject, new UsageViewPresentation());
    myTable = new JBTable(new MyKeyValueModel());
    DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
    renderer.putClientProperty("html.disable", Boolean.TRUE);
    myTable.setDefaultRenderer(String.class, renderer);
    myTable.getSelectionModel().addListSelectionListener(e -> {
      updateUsagePreview(myTable);
    });

    AnActionButtonRunnable removeAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        TableUtil.removeSelectedItems(myTable);
        myTable.repaint();
        updateUsagePreview(myTable);
      }
    };

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTable).setRemoveAction(removeAction);
    if (myMarkAsNonNlsButtonIcon != null) {
      AnAction markAsNonNls = new DumbAwareAction(JavaI18nBundle.message("action.text.mark.as.nonnls"), null, myMarkAsNonNlsButtonIcon) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          TableUtil.stopEditing(myTable);
          List<Pair<Integer, I18nizedPropertyData<D>>> selection = getSelectedDataWithIndices();
          boolean mark = shouldMarkAsNonNls(selection);
          for (Pair<Integer, I18nizedPropertyData<D>> dataWithIndex : selection) {
            myKeyValuePairs.set(dataWithIndex.first, dataWithIndex.second.setMarkAsNonNls(mark));
          }
          myTable.repaint();
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          List<Pair<Integer, I18nizedPropertyData<D>>> selection = getSelectedDataWithIndices();
          e.getPresentation().setEnabled(!selection.isEmpty());
          e.getPresentation().setText(shouldMarkAsNonNls(selection)
                                      ? JavaI18nBundle.message("action.text.mark.as.nonnls")
                                      : JavaI18nBundle.message("action.text.unmark.as.nonnls"));
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          // getSelectedDataWithIndices() is used which queries swing
          return ActionUpdateThread.EDT;
        }

        private boolean shouldMarkAsNonNls(List<Pair<Integer, I18nizedPropertyData<D>>> selection) {
          return !ContainerUtil.and(selection, data -> data.second.markAsNonNls());
        }
      };
      markAsNonNls.setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.ALT_DOWN_MASK)));
      decorator.addExtraAction(markAsNonNls);
    }
    splitter.setFirstComponent(decorator.createPanel());
    splitter.setSecondComponent(myUsagePreviewPanel);
    return splitter;
  }

  @NotNull
  private List<Pair<Integer, I18nizedPropertyData<D>>> getSelectedDataWithIndices() {
    int[] rows = myTable.getSelectedRows();
    List<Pair<Integer, I18nizedPropertyData<D>>> selection = new ArrayList<>(rows.length);
    for (int row : rows) {
      if (0 <= row && row < myKeyValuePairs.size()) {
        selection.add(Pair.create(row, myKeyValuePairs.get(row)));
      }
    }
    return selection;
  }

  private void updateUsagePreview(JBTable table) {
    int index = table.getSelectionModel().getLeadSelectionIndex();
    if (index != -1 && index < myKeyValuePairs.size()) {
      myUsagePreviewPanel.updateLayout(myUsagePreviewProvider.apply(myKeyValuePairs.get(index).contextData()));
    }
    else {
      myUsagePreviewPanel.updateLayout(null);
    }
  }

  @Override
  protected void doOKAction() {
    TableUtil.stopEditing(myTable);
    PropertiesComponent.getInstance(myProject).setValue(LAST_USED_PROPERTIES_FILE, (String)myPropertiesFile.getSelectedItem());
    PropertiesComponent.getInstance(myProject).setValue(LAST_USED_CONTEXT, getContextString());
    if (myRBEditorTextField != null) {
      myRBEditorTextField.addCurrentTextToHistory();
    }
    super.doOKAction();
  }

  @Nullable
  private String getContextString() {
    return myContextModules.stream().map(Module::getName).min(Comparator.naturalOrder()).orElse(null);
  }

  @Override
  protected void dispose() {
    Disposer.dispose(myUsagePreviewPanel);
    super.dispose();
  }

  public static ResourceBundleManager getResourceBundleManager(@NotNull Project project, @NotNull Set<PsiFile> contextFiles) {
    ResourceBundleManager bundleManager = null;
    try {
      bundleManager = ResourceBundleManager.getManager(contextFiles, project);
      LOG.assertTrue(bundleManager != null);
    }
    catch (ResourceBundleManager.ResourceBundleNotFoundException e) {
      LOG.error(e);
    }
    return bundleManager;
  }

  private class MyKeyValueModel extends AbstractTableModel implements ItemRemovable {
    @Override
    public int getRowCount() {
      return myKeyValuePairs.size();
    }

    @Override
    public String getColumnName(int column) {
      return column == 0 ? JavaI18nBundle.message("key.column.name") : JavaI18nBundle.message("value.column.name");
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 0 && 0 <= rowIndex && rowIndex < myKeyValuePairs.size() && !myKeyValuePairs.get(rowIndex).markAsNonNls();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      I18nizedPropertyData<?> data = myKeyValuePairs.get(rowIndex);
      if (columnIndex == 0) {
        return data.markAsNonNls() ? "will be marked as NonNls" : data.key();
      }
      return data.value();
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (columnIndex == 0) {
        I18nizedPropertyData<D> bean = myKeyValuePairs.get(rowIndex);
        myKeyValuePairs.set(rowIndex, bean.changeKey((String)aValue));
      }
    }

    @Override
    public void removeRow(int idx) {
      myKeyValuePairs.remove(idx);
    }
  }
}
