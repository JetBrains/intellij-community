package com.intellij.cvsSupport2.cvsoperations.cvsAdd.ui;

import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.cvsSupport2.ui.Options;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.util.Collection;

/**
 * author: lesya
 */
public abstract class AbstractAddOptionsDialog extends OptionsDialog {
  protected final Options myOptions;

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsoperations.cvsAdd.ui.AbstractAddOptionsDialog");

  public static AbstractAddOptionsDialog createDialog(Project project,
                                                      Collection<AddedFileInfo> files,
                                                      Options options){
    LOG.assertTrue(files.size() > 0);
    if ((files.size() > 1) || firstElementContainsChildren(files))
      return new AddMultiplyFilesOptionsDialog(project, files, options);
    else
      return new AddOneFileOptionsDialog(project, options, files.iterator().next());
  }

  private static boolean firstElementContainsChildren(Collection<AddedFileInfo> files) {
    return (files.iterator().next().getChildCount() > 0);
  }


  public AbstractAddOptionsDialog(Project project, Options options) {
    super(project);
    myOptions = options;
    UIUtil.setActionNameAndMnemonic(com.intellij.CvsBundle.message("button.text.add.to.cvs"), getOKAction());
    UIUtil.setActionNameAndMnemonic(com.intellij.CvsBundle.message("button.text.dont.add.to.cvs"), getCancelAction());

  }

  protected boolean isToBeShown() {
    return myOptions.isToBeShown(myProject);
  }

  protected void setToBeShown(boolean value, boolean onOk) {
    myOptions.setToBeShown(value, myProject, onOk);
  }

  protected boolean shouldSaveOptionsOnCancel() {
    return true;
  }
}
