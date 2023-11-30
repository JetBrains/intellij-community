// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.crlf;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;
import static com.intellij.util.ui.UIUtil.DEFAULT_VGAP;
import static git4idea.crlf.GitCrlfUtil.*;

/**
 * Warns the user that CRLF line separators are about to be committed to the repository.
 * Provides some additional information and proposes to set {@code git config --global core.autocrlf true/input}.
 *
 * @author Kirill Likhodedov
 * @see GitCrlfProblemsDetector
 */
public class GitCrlfDialog extends DialogWrapper {

  public static final int SET = DialogWrapper.OK_EXIT_CODE;
  public static final int DONT_SET = DialogWrapper.NEXT_USER_EXIT_CODE;
  public static final int CANCEL = DialogWrapper.CANCEL_EXIT_CODE;
  private JBCheckBox myDontWarn;

  public GitCrlfDialog(@Nullable Project project) {
    super(project, false);

    setOKButtonText(GitBundle.message("button.crlf.fix.dialog.fix.and.commit"));
    setTitle(GitBundle.message("title.crlf.fix.dialog"));
    getCancelAction().putValue(DialogWrapper.FOCUSED_ACTION, true);

    init();
  }

  @Override
  protected Action @NotNull [] createActions() {
    DialogWrapperExitAction skipButton = new DialogWrapperExitAction(GitBundle.message("button.crlf.fix.dialog.commit.as.is"), DONT_SET);
    return new Action[]{getHelpAction(), getOKAction(), skipButton, getCancelAction()};
  }

  @Override
  protected JComponent createCenterPanel() {
    String warningText = new HtmlBuilder()
      .appendRaw(GitBundle.message("text.crlf.fix.dialog.description.warning",
                                   HtmlChunk.text(ATTRIBUTE_KEY).code(), HtmlChunk.text(RECOMMENDED_VALUE).code()))
      .wrapWithHtmlBody().toString();
    String proposedFixText = new HtmlBuilder()
      .appendRaw(GitBundle.message("text.crlf.fix.dialog.description.proposed.fix", HtmlChunk.text(SUGGESTED_FIX).code()))
      .wrapWithHtmlBody().toString();

    JLabel icon = new JLabel(UIUtil.getWarningIcon(), SwingConstants.LEFT);
    myDontWarn = new JBCheckBox(GitBundle.message("checkbox.dont.warn.again"));

    JPanel rootPanel = new JPanel(new GridBagLayout());
    GridBag g = new GridBag()
      .setDefaultInsets(new Insets(0, 6, DEFAULT_VGAP, DEFAULT_HGAP))
      .setDefaultAnchor(GridBagConstraints.LINE_START)
      .setDefaultFill(GridBagConstraints.HORIZONTAL);

    rootPanel.add(icon, g.nextLine().next().coverColumn(4));
    rootPanel.add(new JBLabel(warningText), g.next());
    rootPanel.add(new JBLabel(proposedFixText), g.nextLine().next().next().pady(DEFAULT_HGAP));
    rootPanel.add(myDontWarn, g.nextLine().next().next().insets(0, 0, 0, 0));

    return rootPanel;
  }

  public boolean dontWarnAgain() {
    return myDontWarn.isSelected();
  }

  @Override
  protected @Nullable String getHelpId() {
    return "reference.VersionControl.Git.CrlfWarning";
  }

}
