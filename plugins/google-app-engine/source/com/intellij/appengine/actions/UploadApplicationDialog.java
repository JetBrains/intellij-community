package com.intellij.appengine.actions;

import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;

/**
 * @author nik
 */
public class UploadApplicationDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JComboBox myFacetComboBox;

  public UploadApplicationDialog(Project project) {
    super(project, true);
    setTitle("Upload Application");
    setModal(true);
    AppEngineUtil.setupAppEngineFacetCombobox(project, myFacetComboBox);
    setOKButtonText("Upload");
    init();
  }

  public AppEngineFacet getSelectedFacet() {
    return (AppEngineFacet)myFacetComboBox.getSelectedItem();
  }

  protected JComponent createCenterPanel() {
    return myMainPanel;
  }
}
