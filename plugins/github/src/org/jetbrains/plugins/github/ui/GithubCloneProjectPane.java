package org.jetbrains.plugins.github.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkAdapter;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.RepositoryInfo;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
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
  private JTextPane myDetailsTextField;
  private final GithubCloneProjectDialog myDialog;

  public GithubCloneProjectPane(final GithubCloneProjectDialog dialog) {
    myDialog = dialog;
    mySelectRepositoryComboBox.setRenderer(new ListCellRendererWrapper<RepositoryInfo>(mySelectRepositoryComboBox.getRenderer()){
      @Override
      public void customize(final JList list, final RepositoryInfo value, final int index, final boolean selected, final boolean cellHasFocus) {
        if (value != null) {
          setText(value.getName());
        }
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
        updateDetails(repositoryInfo);
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

    myDetailsTextField.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(final HyperlinkEvent e) {
        BrowserUtil.launchBrowser(e.getURL().toExternalForm());
      }
    });
    myDetailsTextField.setBackground(myPanel.getBackground());
    myDetailsTextField.setCursor(new Cursor(Cursor.HAND_CURSOR));
  }

  private void updateDetails(final RepositoryInfo repositoryInfo) {
    if (repositoryInfo == null){
      myDetailsTextField.setText("<html>No repository selected</html>");
      return;
    }
    myDetailsTextField.setText(
      "<html><a href=\"" + repositoryInfo.getUrl() + "\">" + "Details on '" + repositoryInfo.getName() + "' here" + "</a></html>");
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
        final FileChooserDialog fileChooser = FileChooserFactory.getInstance().createFileChooser(fileChooserDescriptor, myPanel);
        final VirtualFile[] files = fileChooser.choose(null, null);
        if (files.length > 0) {
          myTextFieldWithBrowseButton.setText(files[0].getPath());
        }
      }
    });
  }

  public void setAvailableRepos(final List<RepositoryInfo> repos) {
    mySelectRepositoryComboBox.setModel(new DefaultComboBoxModel(repos.toArray(new Object[repos.size()])));
    final RepositoryInfo preselectedRepository = (RepositoryInfo)mySelectRepositoryComboBox.getSelectedItem();
    if (preselectedRepository != null) {
      myProjectNameText.setText(preselectedRepository.getName());
    }
    updateDetails(preselectedRepository);
  }

  public void setSelectedPath(final String path) {
    myTextFieldWithBrowseButton.setText(path);
  }
}
