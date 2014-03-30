package org.zmlx.hg4idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;
import static com.intellij.util.ui.UIUtil.DEFAULT_VGAP;

/**
 * @author Nadya Zabrodina
 */
public class HgBookmarkDialog extends DialogWrapper {
  @NotNull private JBTextField myBookmarkName;
  @NotNull private JBCheckBox myActiveCheckbox;

  public HgBookmarkDialog(@Nullable Project project) {
    super(project, false);
    setTitle("Create Bookmark");
    setResizable(false);
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

  @Override
  @NotNull
  protected JComponent createCenterPanel() {

    JPanel contentPanel = new JPanel(new GridBagLayout());
    GridBag g = new GridBag()
      .setDefaultInsets(new Insets(0, 0, DEFAULT_VGAP, DEFAULT_HGAP))
      .setDefaultAnchor(GridBagConstraints.LINE_START)
      .setDefaultFill(GridBagConstraints.HORIZONTAL);

    JLabel icon = new JLabel(UIUtil.getQuestionIcon(), SwingConstants.LEFT);
    myBookmarkName = new JBTextField(13);

    JBLabel bookmarkLabel = new JBLabel("Bookmark name:");
    bookmarkLabel.setLabelFor(myBookmarkName);

    myActiveCheckbox = new JBCheckBox("Inactive", false);

    contentPanel.add(icon, g.nextLine().next().coverColumn(3).pady(DEFAULT_HGAP));
    contentPanel.add(bookmarkLabel, g.next().fillCellNone().insets(new Insets(0, 6, DEFAULT_VGAP, DEFAULT_HGAP)));
    contentPanel.add(myBookmarkName, g.next().coverLine().setDefaultWeightX(1));
    contentPanel.add(myActiveCheckbox, g.nextLine().next().next().coverLine(2));
    return contentPanel;
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
