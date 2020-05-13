// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n.batch;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.ResourceBundleManager;
import com.intellij.lang.properties.references.I18nUtil;
import com.intellij.lang.properties.references.I18nizeQuickFixDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
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
import com.intellij.util.ui.ItemRemovable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class I18nizeMultipleStringsDialog<D> extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(I18nizeMultipleStringsDialog.class);
  private static final @NonNls String LAST_USED_PROPERTIES_FILE = "LAST_USED_PROPERTIES_FILE";
  private static final @NonNls String LAST_USED_CONTEXT = "I18N_FIX_LAST_USED_CONTEXT";

  @NotNull private final Project myProject;
  private final List<I18nizedPropertyData<D>> myKeyValuePairs;
  private final Function<D, List<UsageInfo>> myUsagePreviewProvider;
  private final Set<Module> myContextModules;
  private final @Nullable ResourceBundleManager myResourceBundleManager;
  private JComboBox<String> myPropertiesFile;
  private UsagePreviewPanel myUsagePreviewPanel;
  private JBTable myTable;
  private final Icon myMarkAsNonNlsButtonIcon;

  public I18nizeMultipleStringsDialog(@NotNull Project project,
                                      @NotNull List<I18nizedPropertyData<D>> keyValuePairs,
                                      @NotNull Set<PsiFile> contextFiles,
                                      @NotNull Function<D, List<UsageInfo>> usagePreviewProvider, Icon markAsNonNlsButtonIcon) {
    super(project, true);
    myProject = project;
    myKeyValuePairs = keyValuePairs;
    myUsagePreviewProvider = usagePreviewProvider;
    ResourceBundleManager resourceBundleManager;
    try {
      resourceBundleManager = ResourceBundleManager.getManager(contextFiles, project);
    }
    catch (ResourceBundleManager.ResourceBundleNotFoundException e) {
      LOG.error(e);
      resourceBundleManager = null;
    }
    myResourceBundleManager = resourceBundleManager;
    myMarkAsNonNlsButtonIcon = markAsNonNlsButtonIcon;
    myContextModules = contextFiles.stream().map(ModuleUtilCore::findModuleForFile).filter(Objects::nonNull).collect(Collectors.toSet());
    setTitle(PropertiesBundle.message("i18nize.multiple.strings.dialog.title"));
    init();
  }

  @Override
  protected @Nullable String getDimensionServiceKey() {
    return "i18nInBatch";
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    List<String> files = myResourceBundleManager != null ? myResourceBundleManager.suggestPropertiesFiles(myContextModules)
                                                         : I18nUtil.defaultSuggestPropertiesFiles(myProject, myContextModules);
    myPropertiesFile = new ComboBox<>(ArrayUtil.toStringArray(files));
    new ComboboxSpeedSearch(myPropertiesFile);
    LabeledComponent<JComboBox<String>> component = new LabeledComponent<>();
    component.setText(JavaI18nBundle.message("property.file"));
    component.setComponent(myPropertiesFile);
    myPropertiesFile.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        PropertiesFile propertiesFile = getPropertiesFile();
        if (propertiesFile != null) {
          for (int i = 0; i < myKeyValuePairs.size(); i++) {
            I18nizedPropertyData<D> data = myKeyValuePairs.get(i);
            I18nizedPropertyData<D> updated = data.changeKey(
              I18nizeQuickFixDialog.suggestUniquePropertyKey(data.getValue(), data.getKey(), propertiesFile)
            );
            myKeyValuePairs.set(i, updated);
          }
        }
      }
    });

    if (!files.isEmpty()) {
      String contextString = getContextString();
      String preselectedFile;
      if (contextString != null && contextString.equals(PropertiesComponent.getInstance(myProject).getValue(LAST_USED_CONTEXT))) {
        preselectedFile = PropertiesComponent.getInstance(myProject).getValue(LAST_USED_PROPERTIES_FILE);
      }
      else {
        preselectedFile = null;
      }
      myPropertiesFile.setSelectedItem(ObjectUtils.notNull(preselectedFile, files.get(0)));
    }
    return component;
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

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
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
      AnActionButton markAsNonNls = new AnActionButton(JavaI18nBundle.message("action.text.mark.as.nonnls"), myMarkAsNonNlsButtonIcon) {
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
        public void updateButton(@NotNull AnActionEvent e) {
          List<Pair<Integer, I18nizedPropertyData<D>>> selection = getSelectedDataWithIndices();
          e.getPresentation().setEnabled(!selection.isEmpty());
          e.getPresentation().setText(shouldMarkAsNonNls(selection)
                                      ? JavaI18nBundle.message("action.text.mark.as.nonnls")
                                      : JavaI18nBundle.message("action.text.unmark.as.nonnls"));
        }

        private boolean shouldMarkAsNonNls(List<Pair<Integer, I18nizedPropertyData<D>>> selection) {
          return !selection.stream().allMatch(data -> data.second.isMarkAsNonNls());
        }
      };
      markAsNonNls.setShortcut(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.ALT_DOWN_MASK)));
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
    if (index != -1) {
      myUsagePreviewPanel.updateLayout(myUsagePreviewProvider.apply(myKeyValuePairs.get(index).getContextData()));
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
    public Class<?> getColumnClass(int columnIndex) {
      return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 0 && 0 <= rowIndex && rowIndex < myKeyValuePairs.size() && !myKeyValuePairs.get(rowIndex).isMarkAsNonNls();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      I18nizedPropertyData<?> data = myKeyValuePairs.get(rowIndex);
      if (columnIndex == 0) {
        return data.isMarkAsNonNls() ? "will be marked as NonNls" : data.getKey();
      }
      return data.getValue();
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
