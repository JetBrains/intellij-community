package com.intellij.appengine.facet;

import com.intellij.appengine.sdk.impl.AppEngineSdkUtil;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.facet.Facet;
import com.intellij.facet.ui.*;
import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

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
  private JTextField myUserEmailField;
  private JPasswordField myPasswordField;
  private JPanel myFilesPanel;
  private AppEngineSdkEditor mySdkEditor;
  private DefaultListModel myFilesListModel;

  public AppEngineFacetEditor(AppEngineFacetConfiguration facetConfiguration, FacetEditorContext context, FacetValidatorsManager validatorsManager) {
    myFacetConfiguration = facetConfiguration;
    myContext = context;
    mySdkEditor = new AppEngineSdkEditor(myContext.getProject());
    validatorsManager.registerValidator(new FacetEditorValidator() {
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
    descriptor.setRoots(rootModel.getSourceRoots());
    final VirtualFile[] files = FileChooser.chooseFiles(descriptor, myContext.getProject(), null);
    for (VirtualFile file : files) {
      myFilesListModel.addElement(file.getPath());
    }
  }

  @Nls
  public String getDisplayName() {
    return "Google App Engine";
  }

  public JComponent createComponent() {
    mySdkEditorPanel.add(BorderLayout.CENTER, mySdkEditor.getMainComponent());
    return myMainPanel;
  }

  public boolean isModified() {
    return myRunEnhancerOnMakeCheckBox.isSelected() != myFacetConfiguration.isRunEnhancerOnMake()
           || !mySdkEditor.getPath().equals(myFacetConfiguration.getSdkHomePath())
           || !getConfiguredFiles().equals(myFacetConfiguration.getFilesToEnhance())
           || PersistenceApiComboboxUtil.getSelectedApi(myPersistenceApiComboBox) != myFacetConfiguration.getPersistenceApi()
           || !myUserEmailField.getText().equals(myFacetConfiguration.getUserEmail())
           || !myFacetConfiguration.getPassword().equals(new String(myPasswordField.getPassword()));
  }

  private List<String> getConfiguredFiles() {
    final List<String> files = new ArrayList<String>();
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
    myFacetConfiguration.setUserEmail(myUserEmailField.getText());
    myFacetConfiguration.setEncryptedPassword(PasswordUtil.encodePassword(String.valueOf(myPasswordField.getPassword())));
  }

  public void reset() {
    mySdkEditor.setPath(myFacetConfiguration.getSdkHomePath());
    if (myContext.isNewFacet() && myFacetConfiguration.getSdkHomePath().length() == 0) {
      mySdkEditor.setDefaultPath();
    }
    myFilesListModel.removeAllElements();
    fillFilesList(myFacetConfiguration.getFilesToEnhance());
    myRunEnhancerOnMakeCheckBox.setSelected(myFacetConfiguration.isRunEnhancerOnMake());
    myPersistenceApiComboBox.setSelectedItem(myFacetConfiguration.getPersistenceApi().getName());
    myUserEmailField.setText(myFacetConfiguration.getUserEmail());
    myPasswordField.setText(myFacetConfiguration.getPassword());
  }

  private void fillFilesList(final List<String> paths) {
    for (String path : paths) {
      myFilesListModel.addElement(path);
    }
  }

  public void disposeUIResources() {
  }

  @Override
  public void onFacetInitialized(@NotNull Facet facet) {
    ((AppEngineFacet)facet).getSdk().getOrCreateAppServer();
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
          setForeground(Color.RED);
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
