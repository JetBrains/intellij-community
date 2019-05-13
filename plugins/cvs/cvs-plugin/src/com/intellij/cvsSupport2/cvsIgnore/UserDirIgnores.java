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
package com.intellij.cvsSupport2.cvsIgnore;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.util.SimpleStringPattern;

import java.io.File;
import java.util.List;

/**
 * author: lesya
 */
public class UserDirIgnores{

  private List<SimpleStringPattern> myPatterns;
  @NonNls private static final File ourUserHomeCVSIgnoreFile;

  static {
    final String home = System.getenv().get("HOME");
    if (home != null) {
      final File homeDir = new File(home);
      if (homeDir.exists() && homeDir.isDirectory()) {
        ourUserHomeCVSIgnoreFile = new File(homeDir, CvsUtil.CVS_IGNORE_FILE);
      } else {
        ourUserHomeCVSIgnoreFile = new File(SystemProperties.getUserHome() + "/" + CvsUtil.CVS_IGNORE_FILE);
      }
    } else {
      ourUserHomeCVSIgnoreFile = new File(SystemProperties.getUserHome() + "/" + CvsUtil.CVS_IGNORE_FILE);
    }
  }

  public List<SimpleStringPattern> getPatterns(){
    if (myPatterns == null){
      myPatterns = createUserDirIgnoredFilesInfo();
    }
    return myPatterns;
  }

  private static List<SimpleStringPattern> createUserDirIgnoredFilesInfo() {
    final File file = userHomeCvsIgnoreFile();
    ApplicationManager.getApplication().invokeLater(() -> CvsVfsUtil.refreshAndFindFileByIoFile(file), ModalityState.NON_MODAL);
    return IgnoredFilesInfoImpl.getPattensFor(file);
  }

  public static File userHomeCvsIgnoreFile() {
    return ourUserHomeCVSIgnoreFile;
  }

  public void clearInfo() {
    myPatterns = null;
  }
}
