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

import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.util.ui.FileLabel;

import java.awt.*;

/**
 * author: lesya
 */
public abstract class AbstractAddFileConfirmationPanel {
  protected final AddedFileInfo myAddedFileInfo;

  public static AbstractAddFileConfirmationPanel createOn(AddedFileInfo info){
    return info.getFile().isDirectory() ?
           new AddDirectoryConfirmationPanel(info) : new AddFileConfirmationPanel(info);
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
