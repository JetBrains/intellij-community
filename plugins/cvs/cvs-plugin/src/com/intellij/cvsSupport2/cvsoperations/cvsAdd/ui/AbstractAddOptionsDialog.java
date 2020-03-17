/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsoperations.cvsAdd.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.cvsSupport2.ui.Options;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.util.ui.UIUtil;

import java.util.Collection;

/**
 * author: lesya
 */
public abstract class AbstractAddOptionsDialog extends OptionsDialog {
  protected final Options myOptions;

  private static final Logger LOG = Logger.getInstance(AbstractAddOptionsDialog.class);

  public static AbstractAddOptionsDialog createDialog(Project project,
                                                      Collection<AddedFileInfo> files,
                                                      Options options){
    LOG.assertTrue(files.size() > 0);
    if ((files.size() > 1) || firstElementContainsChildren(files))
      return new AddMultipleFilesOptionsDialog(project, files, options);
    else
      return new AddOneFileOptionsDialog(project, options, files.iterator().next());
  }

  private static boolean firstElementContainsChildren(Collection<AddedFileInfo> files) {
    return files.iterator().next().getChildCount() > 0;
  }

  public AbstractAddOptionsDialog(Project project, Options options) {
    super(project);
    myOptions = options;
    UIUtil.setActionNameAndMnemonic(CvsBundle.message("button.text.add.to.cvs"), getOKAction());
  }

  @Override
  protected boolean isToBeShown() {
    return myOptions.isToBeShown(myProject);
  }

  @Override
  protected void setToBeShown(boolean value, boolean onOk) {
    myOptions.setToBeShown(value, myProject, onOk);
  }

  @Override
  protected boolean shouldSaveOptionsOnCancel() {
    return true;
  }
}
