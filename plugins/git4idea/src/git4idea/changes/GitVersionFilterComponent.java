package git4idea.changes;

import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.StandardVersionFilterComponent;

import javax.swing.*;

/**
 * The component for version filter
 */
public class GitVersionFilterComponent extends StandardVersionFilterComponent<ChangeBrowserSettings> {

  /**
   * The constructor
   *
   * @param showDateFilter the filter component
   */
  public GitVersionFilterComponent(boolean showDateFilter) {
    super(showDateFilter);
    init(new ChangeBrowserSettings());
  }

  /**
   * {@inheritDoc}
   */
  public JComponent getComponent() {
    return (JComponent)getStandardPanel();
  }
}
