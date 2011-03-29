package org.jetbrains.plugins.github.ui;

import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.StringComboboxEditor;
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
  private TextFieldWithBrowseButton myTextFieldWithBrowseButton;
  private JTextField myProjectNameText;
  private ComboBox myRepositoryComboBox;
  private final GithubCloneProjectDialog myDialog;

  public GithubCloneProjectPane(final GithubCloneProjectDialog dialog) {
    myDialog = dialog;
    final EditorComboBoxEditor comboEditor = new StringComboboxEditor(ProjectManager.getInstance().getDefaultProject(), FileTypes.PLAIN_TEXT, myRepositoryComboBox);
    myRepositoryComboBox.setEditor(comboEditor);
    ((EditorTextField) comboEditor.getEditorComponent()).addDocumentListener(
      new DocumentAdapter() {
        @Override
        public void beforeDocumentChange(final com.intellij.openapi.editor.event.DocumentEvent e) {
          updateControls();
        }

        @Override
        public void documentChanged(final com.intellij.openapi.editor.event.DocumentEvent e) {
          updateControls();
        }
      });
    myRepositoryComboBox.setRenderer(new EditorComboBoxRenderer(comboEditor));
    myRepositoryComboBox.setEditable(true);

    myProjectNameText.getDocument().addDocumentListener(new DocumentListener() {
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
    });
    myTextFieldWithBrowseButton.getChildComponent().getDocument().addDocumentListener(new DocumentListener() {
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
    });
  }

  public JComponent getPanel() {
    return myPanel;
  }

  public JComponent getPreferrableFocusComponent() {
    return myRepositoryComboBox;
  }

  public String getSelectedRepository(){
    return (String) myRepositoryComboBox.getEditor().getItem();
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

  public void setAvailableRepos(final List<String> repos) {
    myRepositoryComboBox.setModel(new DefaultComboBoxModel(ArrayUtil.toObjectArray(repos)));
    updateControls();
  }

  private void updateControls() {
    final String preselectedRepository = (String)myRepositoryComboBox.getEditor().getItem();
    if (preselectedRepository != null) {
      final int i = preselectedRepository.lastIndexOf('/');
      myProjectNameText.setText(i != -1 ? preselectedRepository.substring(i + 1) : "");
    }
    myDialog.updateOkButton();
  }

  public void setSelectedPath(final String path) {
    myTextFieldWithBrowseButton.setText(path);
  }
}
