package com.intellij.cvsSupport2.cvsoperations.cvsAdd.ui;

import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.util.ui.FileLabel;
import com.intellij.util.ui.FileLabel;

import java.awt.*;

/**
 * author: lesya
 */
public abstract class AbstractAddFileConfirmationPanel {
  protected final AddedFileInfo myAddedFileInfo;

  public static AbstractAddFileConfirmationPanel createOn(AddedFileInfo info){
    return info.getFile().isDirectory() ?
           new AddDirectoryConfirmationPanel(info) :
           (AbstractAddFileConfirmationPanel)new AddFileConfirmationPanel(info);
  }

  public AbstractAddFileConfirmationPanel(AddedFileInfo addedFileInfo) {
    myAddedFileInfo = addedFileInfo;

  }

  protected void init(){
    FileLabel fileLabel = getFileLabel();
    fileLabel.setShowIcon(false);
    fileLabel.setFile(myAddedFileInfo.getPresentableFile());
    fileLabel.pack();
  }

  protected abstract FileLabel getFileLabel();

  public abstract Component getPanel() ;
}
