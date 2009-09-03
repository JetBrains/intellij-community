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
