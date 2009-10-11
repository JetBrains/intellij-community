/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsIgnore;

import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.cvsSupport2.cvsIgnore.IgnoredFilesInfoImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;

import java.io.File;
import java.util.ArrayList;

import org.jetbrains.annotations.NonNls;

/**
 * author: lesya
 */
public class UserDirIgnores{

  private ArrayList myPatterns;
  @NonNls private static final File ourUserHomeCVSIgnoreFile = new File(System.getProperty("user.home") + "/.cvsignore");

  public ArrayList getPatterns(){
    if (myPatterns == null){
      myPatterns = createUserDirIgnoredFilesInfo();
    }
    return myPatterns;
  }

  private ArrayList createUserDirIgnoredFilesInfo() {
    final File file = userHomeCvsIgnoreFile();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        CvsVfsUtil.refreshAndFindFileByIoFile(file);
      }
    }, ModalityState.NON_MODAL);
    return IgnoredFilesInfoImpl.getPattensFor(file);
  }

  public File userHomeCvsIgnoreFile() {
    return ourUserHomeCVSIgnoreFile;
  }

  public void clearInfo() {
    myPatterns = null;
  }


}
