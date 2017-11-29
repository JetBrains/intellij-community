/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.crlf;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;
import static com.intellij.util.ui.UIUtil.DEFAULT_VGAP;
import static git4idea.crlf.GitCrlfUtil.RECOMMENDED_VALUE;

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

    setOKButtonText("Fix and Commit");
    setCancelButtonText("Cancel");
    setTitle("Line Separators Warning");
    getCancelAction().putValue(DialogWrapper.FOCUSED_ACTION, true);

    init();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[] { getHelpAction(), getOKAction(), new DialogWrapperExitAction("Commit As Is", DONT_SET), getCancelAction() };
  }

  @Override
  protected JComponent createCenterPanel() {
    JLabel description = new JBLabel(
      "<html>You are about to commit CRLF line separators to the Git repository.<br/>" +
      "It is recommended to set the <code>core.autocrlf</code> Git attribute to <code>" + RECOMMENDED_VALUE +
      "</code> to avoid line separator issues.</html>");

    JLabel additionalDescription = new JBLabel(
      "<html>If you choose <b>Fix and Commit</b>, <code>git config --global core.autocrlf " + RECOMMENDED_VALUE + "</code> will be executed.<br/>" +
      "If you choose <b>Commit as Is</b>, the config value won't be changed.</html>");


    JLabel icon = new JLabel(UIUtil.getWarningIcon(), SwingConstants.LEFT);
    myDontWarn = new JBCheckBox("Don't warn again");
    myDontWarn.setMnemonic('w');

    JPanel rootPanel = new JPanel(new GridBagLayout());
    GridBag g = new GridBag()
      .setDefaultInsets(new Insets(0, 6, DEFAULT_VGAP, DEFAULT_HGAP))
      .setDefaultAnchor(GridBagConstraints.LINE_START)
      .setDefaultFill(GridBagConstraints.HORIZONTAL);

    rootPanel.add(icon, g.nextLine().next().coverColumn(4));
    rootPanel.add(description, g.next());
    rootPanel.add(additionalDescription, g.nextLine().next().next().pady(DEFAULT_HGAP));
    rootPanel.add(myDontWarn,  g.nextLine().next().next().insets(0, 0, 0, 0));

    return rootPanel;

  }

  public boolean dontWarnAgain() {
    return myDontWarn.isSelected();
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.CrlfWarning";
  }

}
