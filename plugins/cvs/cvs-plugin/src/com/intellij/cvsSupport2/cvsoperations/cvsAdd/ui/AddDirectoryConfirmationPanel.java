package com.intellij.cvsSupport2.cvsoperations.cvsAdd.ui;

import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.util.ui.FileLabel;
import com.intellij.util.ui.FileLabel;

import javax.swing.*;
import java.awt.*;

/**
 * author: lesya
 */

public class AddDirectoryConfirmationPanel extends AbstractAddFileConfirmationPanel{
  private FileLabel myFileLabel;
  private JPanel myPanel;

  public AddDirectoryConfirmationPanel(final AddedFileInfo addedFileInfo) {
    super(addedFileInfo);
    init();
  }

  public Component getPanel() {
    return myPanel;
  }

  protected FileLabel getFileLabel() {
    return myFileLabel;
  }
}
