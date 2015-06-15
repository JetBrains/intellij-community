package org.zmlx.hg4idea.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgBranchReferenceValidator;
import org.zmlx.hg4idea.util.HgReferenceValidator;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;

import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;
import static com.intellij.util.ui.UIUtil.DEFAULT_VGAP;

public class HgBookmarkDialog extends DialogWrapper {
  @NotNull private HgRepository myRepository;
  @NotNull private JBTextField myBookmarkName;
  @NotNull private JBCheckBox myActiveCheckbox;

  public HgBookmarkDialog(@NotNull HgRepository repository) {
    super(repository.getProject(), false);
    myRepository = repository;
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
    myBookmarkName.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent e) {
        validateFields();
      }
    });

    JBLabel bookmarkLabel = new JBLabel("Bookmark name:");
    bookmarkLabel.setLabelFor(myBookmarkName);

    myActiveCheckbox = new JBCheckBox("Inactive", false);

    contentPanel.add(icon, g.nextLine().next().coverColumn(3).pady(DEFAULT_HGAP));
    contentPanel.add(bookmarkLabel, g.next().fillCellNone().insets(new Insets(0, 6, DEFAULT_VGAP, DEFAULT_HGAP)));
    contentPanel.add(myBookmarkName, g.next().coverLine().setDefaultWeightX(1));
    contentPanel.add(myActiveCheckbox, g.nextLine().next().next().coverLine(2));
    return contentPanel;
  }

  private void validateFields() {
    HgReferenceValidator validator = new HgBranchReferenceValidator(myRepository);
    String name = getName();
    if (!validator.checkInput(name)) {
      String message = validator.getErrorText(name);
      setErrorText(message == null ? "You have to specify bookmark name." : message);
      setOKActionEnabled(false);
      return;
    }
    setErrorText(null);
    setOKActionEnabled(true);
  }

  public boolean isActive() {
    return !myActiveCheckbox.isSelected();
  }

  @Nullable
  public String getName() {
    return myBookmarkName.getText();
  }
}
