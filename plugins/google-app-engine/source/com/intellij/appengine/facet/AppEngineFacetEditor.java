/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.appengine.facet;

import com.intellij.appengine.sdk.impl.AppEngineSdkUtil;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.facet.Facet;
import com.intellij.facet.ui.*;
import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class AppEngineFacetEditor extends FacetEditorTab {
  private final AppEngineFacetConfiguration myFacetConfiguration;
  private final FacetEditorContext myContext;
  private JPanel myMainPanel;
  private JPanel mySdkEditorPanel;
  private JCheckBox myRunEnhancerOnMakeCheckBox;
  private JPanel myFilesToEnhancePanel;
  private JList myFilesList;
  private JComboBox myPersistenceApiComboBox;
  private JPanel myFilesPanel;
  private AppEngineSdkEditor mySdkEditor;
  private DefaultListModel myFilesListModel;

  public AppEngineFacetEditor(AppEngineFacetConfiguration facetConfiguration, FacetEditorContext context, FacetValidatorsManager validatorsManager) {
    myFacetConfiguration = facetConfiguration;
    myContext = context;
    mySdkEditor = new AppEngineSdkEditor(myContext.getProject());
    validatorsManager.registerValidator(new FacetEditorValidator() {
      @NotNull
      @Override
      public ValidationResult check() {
        return AppEngineSdkUtil.checkPath(mySdkEditor.getPath());
      }
    }, mySdkEditor.getComboBox());

    myRunEnhancerOnMakeCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        GuiUtils.enableChildren(myRunEnhancerOnMakeCheckBox.isSelected(), myFilesToEnhancePanel);
        if (myRunEnhancerOnMakeCheckBox.isSelected() && myFilesListModel.isEmpty()) {
          fillFilesList(AppEngineUtil.getDefaultSourceRootsToEnhance(myContext.getRootModel()));
        }
      }
    });

    myFilesListModel = new DefaultListModel();
    myFilesList = new JBList(myFilesListModel);
    myFilesList.setCellRenderer(new FilesListCellRenderer());
    myFilesPanel.add(ToolbarDecorator.createDecorator(myFilesList)
                       .setAddAction(new AnActionButtonRunnable() {
                         @Override
                         public void run(AnActionButton button) {
                           doAdd();
                         }
                       }).disableUpDownActions().createPanel(), BorderLayout.CENTER);

    PersistenceApiComboboxUtil.setComboboxModel(myPersistenceApiComboBox, false);
  }

  private void doAdd() {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, false, false, false, true);
    final ModuleRootModel rootModel = myContext.getRootModel();
    descriptor.setRoots(rootModel.getSourceRoots(JavaModuleSourceRootTypes.SOURCES));
    final VirtualFile[] files = FileChooser.chooseFiles(descriptor, myContext.getProject(), null);
    for (VirtualFile file : files) {
      myFilesListModel.addElement(file.getPath());
    }
  }

  @Nls
  public String getDisplayName() {
    return "Google App Engine";
  }

  @NotNull
  public JComponent createComponent() {
    mySdkEditorPanel.add(BorderLayout.CENTER, mySdkEditor.getMainComponent());
    return myMainPanel;
  }

  public boolean isModified() {
    return myRunEnhancerOnMakeCheckBox.isSelected() != myFacetConfiguration.isRunEnhancerOnMake()
           || !mySdkEditor.getPath().equals(myFacetConfiguration.getSdkHomePath())
           || !getConfiguredFiles().equals(myFacetConfiguration.getFilesToEnhance())
           || PersistenceApiComboboxUtil.getSelectedApi(myPersistenceApiComboBox) != myFacetConfiguration.getPersistenceApi();
  }

  private List<String> getConfiguredFiles() {
    final List<String> files = new ArrayList<>();
    for (int i = 0; i < myFilesListModel.getSize(); i++) {
      files.add((String)myFilesListModel.getElementAt(i));
    }
    return files;
  }

  public void apply() {
    myFacetConfiguration.setSdkHomePath(mySdkEditor.getPath());
    myFacetConfiguration.setRunEnhancerOnMake(myRunEnhancerOnMakeCheckBox.isSelected());
    myFacetConfiguration.setFilesToEnhance(getConfiguredFiles());
    myFacetConfiguration.setPersistenceApi(PersistenceApiComboboxUtil.getSelectedApi(myPersistenceApiComboBox));
  }

  public void reset() {
    mySdkEditor.setPath(myFacetConfiguration.getSdkHomePath());
    if (myContext.isNewFacet() && myFacetConfiguration.getSdkHomePath().length() == 0) {
      mySdkEditor.setDefaultPath();
    }
    myFilesListModel.removeAllElements();
    fillFilesList(myFacetConfiguration.getFilesToEnhance());
    myRunEnhancerOnMakeCheckBox.setSelected(myFacetConfiguration.isRunEnhancerOnMake());
    myPersistenceApiComboBox.setSelectedItem(myFacetConfiguration.getPersistenceApi().getDisplayName());
  }

  private void fillFilesList(final List<String> paths) {
    for (String path : paths) {
      myFilesListModel.addElement(path);
    }
  }

  public void disposeUIResources() {
  }

  @Override
  public String getHelpTopic() {
    return "Google_App_Engine_Facet";
  }

  @Override
  public void onFacetInitialized(@NotNull Facet facet) {
    AppEngineWebIntegration.getInstance().setupDevServer(((AppEngineFacet)facet).getSdk());
  }

  private class FilesListCellRenderer extends DefaultListCellRenderer {
    private FilesListCellRenderer() {
      setUI(new RightAlignedLabelUI());
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value instanceof String) {
        final String path = (String)value;
        final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
        if (file == null) {
          setForeground(JBColor.RED);
          setIcon(null);
        }
        else {
          setForeground(myFilesList.getForeground());
          setIcon(file.isDirectory() ? PlatformIcons.FOLDER_ICON : VirtualFilePresentation.getIcon(file));
        }
        setText(path);
      }
      return rendererComponent;
    }
  }
}
