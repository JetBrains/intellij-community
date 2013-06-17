package org.zmlx.hg4idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * @author Nadya Zabrodina
 */
public class HgBookmarkDialog extends DialogWrapper {
  private JPanel contentPanel;
  private JTextField myRevision;
  private JTextField myBookmarkName;
  private JCheckBox myActiveCheckbox;

  public HgBookmarkDialog(@Nullable Project project) {
    super(project, false);
    setTitle("Create Bookmark");
    setOKActionEnabled(false);
    DocumentListener documentListener = new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        update();
      }

      public void removeUpdate(DocumentEvent e) {
        update();
      }

      public void changedUpdate(DocumentEvent e) {
        update();
      }
    };

    myBookmarkName.getDocument().addDocumentListener(documentListener);
    init();
  }


  protected JComponent createCenterPanel() {
    return contentPanel;
  }

  @NotNull
  public String getRevision() {
    return myRevision.getText();
  }

  public boolean isActive() {
    return !myActiveCheckbox.isSelected();
  }

  public String getName() {
    return myBookmarkName.getText();
  }

  private void update() {
    setOKActionEnabled(validateOptions());
  }

  private boolean validateOptions() {
    return !StringUtil.isEmptyOrSpaces(getName());
  }
}
