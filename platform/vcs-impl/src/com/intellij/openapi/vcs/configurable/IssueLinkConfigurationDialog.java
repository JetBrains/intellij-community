// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.openapi.vcs.IssueNavigationLink;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.text.MessageFormat;
import java.util.ArrayList;


@ApiStatus.Internal
public class IssueLinkConfigurationDialog extends DialogWrapper {
  private JPanel myPanel;
  private JTextField myIssueIDTextField;
  private JTextField myIssueLinkTextField;
  private JLabel myErrorLabel;
  private JTextField myExampleIssueIDTextField;
  private JTextField myExampleIssueLinkTextField;

  protected IssueLinkConfigurationDialog(Project project) {
    super(project, false);
    init();
    DocumentAdapter documentChangeListener = new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        updateFeedback();
      }
    };
    myIssueIDTextField.getDocument().addDocumentListener(documentChangeListener);
    myIssueLinkTextField.getDocument().addDocumentListener(documentChangeListener);
    myExampleIssueIDTextField.getDocument().addDocumentListener(documentChangeListener);

    myIssueIDTextField.setText("Task_([A-Za-z]+)_(\\d+)"); //NON-NLS // placeholder
    myIssueLinkTextField.setText("https://example.com/issue/$1/$2"); //NON-NLS // placeholder
    myExampleIssueIDTextField.setText("Task_DA_113"); //NON-NLS // placeholder
  }

  private void updateFeedback() {
    myErrorLabel.setText(" ");
    try {
      if (myIssueIDTextField.getText().length() > 0) {
        ArrayList<IssueNavigationConfiguration.LinkMatch> matches = new ArrayList<>();
        IssueNavigationConfiguration.findIssueLinkMatches(myExampleIssueIDTextField.getText(), getLink(), matches);
        IssueNavigationConfiguration.LinkMatch firstMatch = ContainerUtil.getFirstItem(matches);
        if (firstMatch != null) {
          myExampleIssueLinkTextField.setText(firstMatch.getTargetUrl());
        }
        else {
          myExampleIssueLinkTextField.setText(VcsBundle.message("add.issue.dialog.issue.no.match"));
        }
      }
    }
    catch (Exception ex) {
      myErrorLabel.setText(VcsBundle.message("add.issue.dialog.invalid.regular.expression", ex.getMessage()));
      myExampleIssueLinkTextField.setText("");
    }
    setOKActionEnabled(myErrorLabel.getText().equals(" "));
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "reference.settings.vcs.issue.navigation.add.link";
  }

  public IssueNavigationLink getLink() {
    return new IssueNavigationLink(myIssueIDTextField.getText(), myIssueLinkTextField.getText());
  }

  public void setLink(final IssueNavigationLink link) {
    myIssueIDTextField.setText(link.getIssueRegexp());
    myIssueLinkTextField.setText(link.getLinkRegexp());
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myIssueIDTextField;
  }
}