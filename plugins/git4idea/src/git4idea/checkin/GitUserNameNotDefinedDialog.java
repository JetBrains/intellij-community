/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.checkin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static com.intellij.util.ui.UIUtil.DEFAULT_HGAP;
import static com.intellij.util.ui.UIUtil.DEFAULT_VGAP;

class GitUserNameNotDefinedDialog extends DialogWrapper {

  @NotNull private final Collection<? extends VirtualFile> myRootsWithUndefinedProps;
  @NotNull private final Collection<? extends VirtualFile> myAllRootsAffectedByCommit;
  @Nullable private final Couple<String> myProposedValues;
  @NotNull private final GitVcsSettings mySettings;

  private JTextField myNameTextField;
  private JTextField myEmailTextField;
  private JBCheckBox myGlobalCheckbox;

  GitUserNameNotDefinedDialog(@NotNull Project project,
                              @NotNull Collection<? extends VirtualFile> rootsWithUndefinedProps,
                              @NotNull Collection<? extends VirtualFile> allRootsAffectedByCommit,
                              @NotNull Map<VirtualFile, Couple<String>> rootsWithDefinedProps) {
    super(project, false);
    myRootsWithUndefinedProps = rootsWithUndefinedProps;
    myAllRootsAffectedByCommit = allRootsAffectedByCommit;
    mySettings = GitVcsSettings.getInstance(project);

    myProposedValues = calcProposedValues(rootsWithDefinedProps);

    setTitle(GitBundle.message("title.user.name.email.not.specified"));
    setOKButtonText(GitBundle.message("button.set.name.and.commit"));

    init();
  }

  @Override
  protected ValidationInfo doValidate() {
    String message = GitBundle.message("validation.warning.set.name.email.for.git");
    if (isEmptyOrSpaces(getUserName())) {
      return new ValidationInfo(message, myNameTextField);
    }
    String email = getUserEmail();
    if (isEmptyOrSpaces(email)) {
      return new ValidationInfo(message, myEmailTextField);
    }
    if(!email.contains("@")) {
      return new ValidationInfo(GitBundle.message("validation.error.email.no.at"), myEmailTextField);
    }
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameTextField;
  }

  @Nullable
  private static Couple<String> calcProposedValues(Map<VirtualFile, Couple<String>> rootsWithDefinedProps) {
    if (rootsWithDefinedProps.isEmpty()) {
      return null;
    }
    Iterator<Map.Entry<VirtualFile,Couple<String>>> iterator = rootsWithDefinedProps.entrySet().iterator();
    Couple<String> firstValue = iterator.next().getValue();
    while (iterator.hasNext()) {
      // nothing to propose if there are different values set in different repositories
      if (!firstValue.equals(iterator.next().getValue())) {
        return null;
      }
    }
    return firstValue;
  }

  @Override
  protected JComponent createCenterPanel() {

    JLabel icon = new JLabel(UIUtil.getWarningIcon(), SwingConstants.LEFT);
    JLabel description = new JLabel(getMessageText());

    myNameTextField = new JTextField(20);
    JBLabel nameLabel = new JBLabel(GitBundle.message("label.user.name") + " ");
    nameLabel.setLabelFor(myNameTextField);

    myEmailTextField = new JTextField(20);
    JBLabel emailLabel = new JBLabel(GitBundle.message("label.user.email") + " ");
    emailLabel.setLabelFor(myEmailTextField);

    if (myProposedValues != null) {
      myNameTextField.setText(myProposedValues.getFirst());
      myEmailTextField.setText(myProposedValues.getSecond());
    }
    else {
      myNameTextField.setText(SystemProperties.getUserName());
    }

    myGlobalCheckbox = new JBCheckBox(GitBundle.message("checkbox.set.config.property.globally"), mySettings.shouldSetUserNameGlobally());

    JPanel rootPanel = new JPanel(new GridBagLayout());
    GridBag g = new GridBag()
      .setDefaultInsets(JBUI.insets(0, 0, DEFAULT_VGAP, DEFAULT_HGAP))
      .setDefaultAnchor(GridBagConstraints.LINE_START)
      .setDefaultFill(GridBagConstraints.HORIZONTAL);

    rootPanel.add(description, g.nextLine().next().coverLine(3).pady(DEFAULT_HGAP));
    rootPanel.add(icon, g.nextLine().next().coverColumn(3));
    rootPanel.add(nameLabel, g.next().fillCellNone().insets(JBUI.insets(0, 6, DEFAULT_VGAP, DEFAULT_HGAP)));
    rootPanel.add(myNameTextField, g.next());
    rootPanel.add(emailLabel, g.nextLine().next().next().fillCellNone().insets(JBUI.insets(0, 6, DEFAULT_VGAP, DEFAULT_HGAP)));
    rootPanel.add(myEmailTextField, g.next());
    rootPanel.add(myGlobalCheckbox, g.nextLine().next().next().coverLine(2));

    return rootPanel;
  }

  @Override
  protected JComponent createNorthPanel() {
    return null;
  }

  @NlsContexts.Label
  @NotNull
  private String getMessageText() {
    if (myAllRootsAffectedByCommit.size() == myRootsWithUndefinedProps.size()) {
      return "";
    }
    HtmlBuilder sb = new HtmlBuilder()
      .append(GitBundle.message("label.name.email.not.defined.in.n.roots", myRootsWithUndefinedProps.size()));
    for (VirtualFile root : myRootsWithUndefinedProps) {
      sb.br().append(root.getPresentableUrl());
    }
    return sb.wrapWithHtmlBody().toString();
  }

  public String getUserName() {
    return myNameTextField.getText();
  }

  public String getUserEmail() {
    return myEmailTextField.getText();
  }

  public boolean isGlobal() {
    return myGlobalCheckbox.isSelected();
  }

}
