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
package org.jetbrains.idea.devkit.module;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.FileAppearanceService;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.ui.*;
import com.intellij.ui.table.TableView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.LocalPathCellEditor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.devkit.model.impl.RuntimeResourceRootState;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class RuntimeResourceRootsEditor implements ModuleConfigurationEditor {
  private final ColumnInfo<RuntimeResourceRootState, String> myNameColumn = new ColumnInfo<RuntimeResourceRootState, String>("Name") {
    @Nullable
    @Override
    public String valueOf(RuntimeResourceRootState root) {
      return root.myName;
    }

    @Override
    public boolean isCellEditable(RuntimeResourceRootState root) {
      return true;
    }

    @Override
    public void setValue(RuntimeResourceRootState root, String value) {
      root.myName = value;
      doSaveData();
    }

    @Nullable
    @Override
    public String getPreferredStringValue() {
      return StringUtil.repeat("x", 15);
    }
  };
  private final ColumnInfo<RuntimeResourceRootState, String> myUrlColumn = new ColumnInfo<RuntimeResourceRootState, String>("URL") {
    @Nullable
    @Override
    public String valueOf(RuntimeResourceRootState root) {
      return FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(root.myUrl));
    }

    @Override
    public boolean isCellEditable(RuntimeResourceRootState state) {
      return true;
    }

    @Override
    public void setValue(RuntimeResourceRootState state, String value) {
      state.myUrl = VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(value));
      doSaveData();
    }

    @Nullable
    @Override
    public TableCellEditor getEditor(RuntimeResourceRootState state) {
      return new LocalPathCellEditor(myState.getProject()).fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileDescriptor());
    }
  };
  @NotNull private final ModuleConfigurationState myState;
  private TableView<RuntimeResourceRootState> myTable;
  private ListTableModel<RuntimeResourceRootState> myTableModel;
  private final Disposable myDisposable = Disposer.newDisposable();

  public RuntimeResourceRootsEditor(@NotNull ModuleConfigurationState state) {
    myState = state;
  }

  private RuntimeResourcesConfiguration getExtension() {
    return myState.getRootModel().getModuleExtension(RuntimeResourcesConfiguration.class);
  }

  @Override
  public void saveData() {
    TableUtil.stopEditing(myTable);
    doSaveData();
  }

  private void doSaveData() {
    List<RuntimeResourceRoot> roots = new ArrayList<RuntimeResourceRoot>();
    VirtualFilePointerManager pointerManager = VirtualFilePointerManager.getInstance();
    for (RuntimeResourceRootState rootState : myTableModel.getItems()) {
      roots.add(new RuntimeResourceRoot(rootState.myName, pointerManager.create(rootState.myUrl, myDisposable, null)));
    }
    getExtension().setRoots(roots);
  }

  @Override
  public void moduleStateChanged() {
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Runtime Resources";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    myTableModel = new ListTableModel<RuntimeResourceRootState>(myNameColumn, myUrlColumn);
    for (RuntimeResourceRoot root : getExtension().getRoots()) {
      myTableModel.addRow(new RuntimeResourceRootState(root.getName(), root.getUrl()));
    }
    myTable = new TableView<RuntimeResourceRootState>(myTableModel);
    return ToolbarDecorator.createDecorator(myTable)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor();
          VirtualFile baseDir = ArrayUtil.getFirstElement(myState.getRootModel().getContentRoots());
          VirtualFile root = FileChooser.chooseFile(descriptor, myState.getProject(), baseDir);
          if (root != null) {
            myTableModel.addRow(new RuntimeResourceRootState(root.getName(), root.getUrl()));
            saveData();
          }
        }
      })
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          if (!TableUtil.removeSelectedItems(myTable).isEmpty()) {
            saveData();
          }
        }
      })
      .createPanel();
  }

  @Override
  public boolean isModified() {
    return getExtension().isChanged();
  }

  @Override
  public void apply() throws ConfigurationException {
  }

  @Override
  public void reset() {
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myDisposable);
  }

  private static class RootCellRenderer extends ColoredTableCellRenderer {
    @Override
    protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
      if (value instanceof String) {
        VirtualFile file = VirtualFileManager.getInstance().findFileByUrl((String)value);
        if (file != null) {
          FileAppearanceService.getInstance().forVirtualFile(file).customize(this);
        }
        else {
          FileAppearanceService.getInstance().forInvalidUrl((String)value).customize(this);
        }
      }
    }
  }
}
