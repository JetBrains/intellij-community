package org.jetbrains.plugins.github.ui;

import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.RepositoryInfo;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

/**
 * @author oleg
 * @date 10/20/10
 */
public class GithubCloneProjectPane {
  private JPanel myPanel;
  private JComboBox mySelectRepositoryComboBox;
  private TextFieldWithBrowseButton myTextFieldWithBrowseButton;
  private JTextField myProjectNameText;
  private final GithubCloneProjectDialog myDialog;

  public GithubCloneProjectPane(final GithubCloneProjectDialog dialog) {
    myDialog = dialog;
    mySelectRepositoryComboBox.setRenderer(new ListCellRendererWrapper<RepositoryInfo>(mySelectRepositoryComboBox.getRenderer()){
      @Override
      public void customize(final JList list, final RepositoryInfo value, final int index, final boolean selected, final boolean cellHasFocus) {
        setText(value.getOwner() + "/" + value.getName());
      }
    });
    mySelectRepositoryComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(final ItemEvent e) {
        final RepositoryInfo repositoryInfo = (RepositoryInfo)e.getItem();
        if (repositoryInfo != null) {
          myProjectNameText.setText(repositoryInfo.getName());
          myDialog.updateOkButton();
        }
      }
    });

    final DocumentListener updateOkButtonListener = new DocumentListener() {
      // update Ok button state depending on the current state of the fields
      public void insertUpdate(final DocumentEvent e) {
        myDialog.updateOkButton();
      }

      public void removeUpdate(final DocumentEvent e) {
        myDialog.updateOkButton();
      }

      public void changedUpdate(final DocumentEvent e) {
        myDialog.updateOkButton();
      }
    };
    myProjectNameText.getDocument().addDocumentListener(updateOkButtonListener);
    myTextFieldWithBrowseButton.getChildComponent().getDocument().addDocumentListener(updateOkButtonListener);
  }

  public JComponent getPanel() {
    return myPanel;
  }

  public JComponent getPreferrableFocusComponent() {
    return mySelectRepositoryComboBox;
  }

  public RepositoryInfo getSelectedRepository(){
    return (RepositoryInfo) mySelectRepositoryComboBox.getModel().getSelectedItem();
  }

  public String getSelectedPath(){
    return myTextFieldWithBrowseButton.getText();
  }

  public String getProjectName(){
    return myProjectNameText.getText();
  }

  private void createUIComponents() {
    myTextFieldWithBrowseButton = new TextFieldWithBrowseButton(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        final FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
          public String getName(final VirtualFile virtualFile) {
            return virtualFile.getName();
          }

          @Nullable
          public String getComment(final VirtualFile virtualFile) {
            return virtualFile.getPresentableUrl();
          }
        };
        fileChooserDescriptor.setTitle("Select project destination folder");

        final String preselectedFolderPath = myTextFieldWithBrowseButton.getText();
        final VirtualFile preselectedFolder = LocalFileSystem.getInstance().findFileByPath(preselectedFolderPath);

        FileChooser.chooseFilesWithSlideEffect(fileChooserDescriptor, null, preselectedFolder,
                                               new Consumer<VirtualFile[]>() {
                                                 @Override
                                                 public void consume(VirtualFile[] files) {
                                                   if (files.length > 0) {
                                                     myTextFieldWithBrowseButton.setText(files[0].getPath());
                                                   }
                                                 }
                                               });
      }
    });
  }

  public void setAvailableRepos(final List<RepositoryInfo> repos) {
    mySelectRepositoryComboBox.setModel(new DefaultComboBoxModel(ArrayUtil.toObjectArray(repos)));
    final RepositoryInfo preselectedRepository = (RepositoryInfo)mySelectRepositoryComboBox.getSelectedItem();
    if (preselectedRepository != null) {
      myProjectNameText.setText(preselectedRepository.getName());
    }
  }

  public void setSelectedPath(final String path) {
    myTextFieldWithBrowseButton.setText(path);
  }
}
