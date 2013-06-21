package org.zmlx.hg4idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Nadya Zabrodina
 */
public class HgBookmarkDialog extends DialogWrapper {
  @NotNull private JPanel myContentPanel;
  @NotNull private JTextField myBookmarkName;
  @NotNull private JCheckBox myActiveCheckbox;

  public HgBookmarkDialog(@Nullable Project project) {
    super(project, false);
    setTitle("Create Bookmark");
    init();
  }

  @Override
  @Nullable
  protected String getHelpId() {
    return "reference.mercurial.create.bookmark";
  }

  @Override
  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return myBookmarkName;
  }

  @Override
  @NotNull
  protected String getDimensionServiceKey() {
    return HgBookmarkDialog.class.getName();
  }

  @NotNull
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  public boolean isActive() {
    return !myActiveCheckbox.isSelected();
  }

  @Nullable
  public String getName() {
    return myBookmarkName.getText();
  }

  @Override
  @Nullable
  protected ValidationInfo doValidate() {
    String message = "You have to specify bookmark name.";
    if (StringUtil.isEmptyOrSpaces(getName())) {
      return new ValidationInfo(message, myBookmarkName);
    }
    return null;
  }
}
