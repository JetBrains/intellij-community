package com.intellij.cvsSupport2.cvsoperations.cvsAdd.ui;

import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.ui.AbstractAddFileConfirmationPanel;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.ui.AbstractAddOptionsDialog;
import com.intellij.cvsSupport2.ui.Options;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;

/**
 * author: lesya
 */
public class AddOneFileOptionsDialog extends AbstractAddOptionsDialog{
  private final AddedFileInfo myAddedFileInfo;
  private JPanel myConfirmationPanel;
  private JPanel myPanel;

  public AddOneFileOptionsDialog(Project project,
                                 Options options, AddedFileInfo file) {
    super(project, options);
    myAddedFileInfo = file;
    myAddedFileInfo.setIncluded(true);
    setTitle("Add File " + file.getFile().getName() + " to CVS");
    init();
  }

  protected JComponent createCenterPanel() {
    myConfirmationPanel.setLayout(new BorderLayout());
    myConfirmationPanel.add(AbstractAddFileConfirmationPanel.createOn(myAddedFileInfo).getPanel(),
                            BorderLayout.CENTER);
    return myPanel;
  }

}
