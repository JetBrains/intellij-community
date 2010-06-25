package com.intellij.appengine.facet;

import com.intellij.appengine.sdk.impl.AppEngineSdkImpl;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.facet.Facet;
import com.intellij.facet.ui.*;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.ListUtil;
import com.intellij.ui.RightAlignedLabelUI;
import com.intellij.util.Icons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
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
  private JButton myAddButton;
  private JList myFilesList;
  private JButton myRemoveButton;
  private JComboBox myPersistenceApiComboBox;
  private JTextField myUserEmailField;
  private JPasswordField myPasswordField;
  private AppEngineSdkEditor mySdkEditor;
  private DefaultListModel myFilesListModel;

  public AppEngineFacetEditor(AppEngineFacetConfiguration facetConfiguration, FacetEditorContext context, FacetValidatorsManager validatorsManager) {
    myFacetConfiguration = facetConfiguration;
    myContext = context;
    mySdkEditor = new AppEngineSdkEditor(myContext.getProject(), false);
    validatorsManager.registerValidator(new FacetEditorValidator() {
      @Override
      public ValidationResult check() {
        return AppEngineSdkImpl.checkPath(mySdkEditor.getPath());
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
    myFilesList.setCellRenderer(new FilesListCellRenderer());
    myFilesList.setModel(myFilesListModel);
    myFilesList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateButtons();
      }
    });
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        doAdd();
      }
    });
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ListUtil.removeSelectedItems(myFilesList);
        updateButtons();
      }
    });
    PersistenceApiComboboxUtil.setComboboxModel(myPersistenceApiComboBox, false);
    updateButtons();
  }

  private void doAdd() {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, false, false, false, true);
    descriptor.getRoots().clear();
    final ModuleRootModel rootModel = myContext.getRootModel();
    for (VirtualFile file : rootModel.getSourceRoots()) {
      descriptor.addRoot(file);
    }
    final VirtualFile[] files =
        FileChooserFactory.getInstance().createFileChooser(descriptor, myContext.getProject()).choose(null, myContext.getProject());
    for (VirtualFile file : files) {
      myFilesListModel.addElement(file.getPath());
    }
    updateButtons();
  }

  private void updateButtons() {
    myRemoveButton.setEnabled(myFilesList.getSelectedIndices().length > 0);
  }

  @Nls
  public String getDisplayName() {
    return "Google App Engine";
  }

  public JComponent createComponent() {
    mySdkEditorPanel.add(mySdkEditor.getMainComponent());
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
          setIcon(file.isDirectory() ? Icons.FOLDER_ICON : file.getIcon());
        }
        setText(path);
      }
      return rendererComponent;
    }
  }
}
