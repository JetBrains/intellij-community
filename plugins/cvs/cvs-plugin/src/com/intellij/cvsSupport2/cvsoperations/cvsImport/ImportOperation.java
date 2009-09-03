package com.intellij.cvsSupport2.cvsoperations.cvsImport;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsCommandOperation;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.importcmd.ImportCommand;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.util.IIgnoreFileFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class ImportOperation extends CvsCommandOperation {
  private final ImportDetails myDetails;

  public ImportOperation(ImportDetails details) {
    myDetails = details;
  }

  protected Collection<CvsRootProvider> getAllCvsRoots() {
    return Collections.singleton(myDetails.getCvsRoot());
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    ImportCommand result = new ImportCommand();
    myDetails.prepareCommand(result);
    return result;
  }

  public static ImportOperation createTestInstance(File sourceLocation, CvsEnvironment env) {
    ImportDetails details = new ImportDetails(sourceLocation, com.intellij.CvsBundle.message("import.defaults.vendor"),
                                              com.intellij.CvsBundle.message("import.defaults.release_tag"),
                                              com.intellij.CvsBundle.message("import.defaults.log.message"),
                                              sourceLocation.getName(), env, new ArrayList(), new IIgnoreFileFilter(){
                                                public boolean shouldBeIgnored(AbstractFileObject abstractFileObject, ICvsFileSystem cvsFileSystem) {
                                                  return false;
                                                }
                                              });
    return new ImportOperation(details);
  }

  public int getFilesToProcessCount() {
    return 2 * myDetails.getTotalFilesInSourceDirectory();
  }

  protected String getOperationName() {
    return "import";
  }

  protected IIgnoreFileFilter getIgnoreFileFilter() {
    return myDetails.getIgnoreFileFilter();
  }
}
