package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

public class VcsCommitMessageMarginConfigurable extends VcsCheckBoxWithSpinnerConfigurable {

  private final VcsConfiguration myConfiguration;

  public VcsCommitMessageMarginConfigurable(Project project) {
    super(project, VcsBundle.message("configuration.commit.message.margin.prompt"), "");
    myConfiguration = VcsConfiguration.getInstance(myProject);
  }

  @Override
  protected SpinnerNumberModel createSpinnerModel() {
    final int columns = myConfiguration.COMMIT_MESSAGE_MARGIN_SIZE;
    return new SpinnerNumberModel(columns, 0, 10000, 1);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return VcsBundle.message("configuration.commit.message.margin.title");
  }

  @Override
  public boolean isModified() {
    if (myHighlightRecentlyChanged.isSelected() != myConfiguration.USE_COMMIT_MESSAGE_MARGIN) {
      return true;
    }

    if (!Comparing.equal(myHighlightInterval.getValue(), myConfiguration.COMMIT_MESSAGE_MARGIN_SIZE)) {
      return true;
    }

    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    myConfiguration.USE_COMMIT_MESSAGE_MARGIN = myHighlightRecentlyChanged.isSelected();
    myConfiguration.COMMIT_MESSAGE_MARGIN_SIZE = ((Number) myHighlightInterval.getValue()).intValue();
  }

  @Override
  public void reset() {
    myHighlightRecentlyChanged.setSelected(myConfiguration.USE_COMMIT_MESSAGE_MARGIN);
    myHighlightInterval.setValue(myConfiguration.COMMIT_MESSAGE_MARGIN_SIZE);
    myHighlightInterval.setEnabled(myHighlightRecentlyChanged.isSelected());
  }
}
