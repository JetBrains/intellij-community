package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import org.netbeans.lib.cvsclient.CvsRoot;

import java.io.File;

/**
 * author: lesya
 */
public class CvsRootOnEnvironment extends CvsRootProvider{
  public CvsRootOnEnvironment(File rootFile, CvsEnvironment env) {
    super(rootFile, env);
  }

  public String getRepository() {
    return myCvsEnvironment.getRepository();
  }

  public CvsRoot getCvsRoot() {
    return myCvsEnvironment.getCvsRoot();
  }

  public RevisionOrDate getRevisionOrDate() {
    return myCvsEnvironment.getRevisionOrDate();
  }
}
