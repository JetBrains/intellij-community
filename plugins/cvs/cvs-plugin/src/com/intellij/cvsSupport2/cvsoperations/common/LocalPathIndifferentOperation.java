package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.ConstantLocalFileReader;
import com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.DeafAdminReader;
import com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.DeafAdminWriter;
import org.netbeans.lib.cvsclient.admin.IAdminReader;
import org.netbeans.lib.cvsclient.admin.IAdminWriter;
import org.netbeans.lib.cvsclient.file.ILocalFileReader;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public abstract class LocalPathIndifferentOperation extends CvsCommandOperation {
  protected final CvsEnvironment myEnvironment;

  public LocalPathIndifferentOperation(CvsEnvironment environment) {
    super(new DeafAdminReader(), new DeafAdminWriter());
    myEnvironment = environment;
  }

  public LocalPathIndifferentOperation(IAdminReader adminReader, IAdminWriter writer, CvsEnvironment environment) {
    super(adminReader, writer);
    myEnvironment = environment;
  }

  public LocalPathIndifferentOperation(IAdminReader adminReader, CvsEnvironment environment) {
    super(adminReader, new DeafAdminWriter());
    myEnvironment = environment;
  }

  public LocalPathIndifferentOperation(IAdminWriter adminWriter, CvsEnvironment environment) {
    super(new DeafAdminReader(), adminWriter);
    myEnvironment = environment;
  }

  protected Collection<CvsRootProvider> getAllCvsRoots() {
    return Collections.singleton(getCvsRootProvider());
  }

  public CvsRootProvider getCvsRootProvider() {
    return CvsRootProvider.createOn(getPathToCommonRoot(), myEnvironment);
  }

  protected File getPathToCommonRoot() {
    File someFile = new File("").getAbsoluteFile();
    if (someFile.isDirectory()) return someFile;
    return someFile.getAbsoluteFile().getParentFile();
  }

  protected ILocalFileReader createLocalFileReader() {
    return ConstantLocalFileReader.FOR_EXISTING_FILE;
  }

  protected boolean shouldMakeChangesOnTheLocalFileSystem() {
    return false;
  }

  public String getLastProcessedCvsRoot() {
    return myEnvironment.getCvsRootAsString();
  }
}
