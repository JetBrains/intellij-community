package com.intellij.cvsSupport2.cvsoperations.cvsImport;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperation;
import com.intellij.cvsSupport2.ui.experts.importToCvs.FileExtension;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;
import org.netbeans.lib.cvsclient.command.importcmd.ImportCommand;
import org.netbeans.lib.cvsclient.util.IIgnoreFileFilter;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;

/**
 * author: lesya
 */

public class ImportDetails {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsoperations.ImportDetails");

  private final File myBaseImportDirectory;
  private final String myVendor;
  private final String myReleaseTag;
  private final String myLogMessage;
  private final String myModuleName;
  private final CvsEnvironment myEnvironment;
  private final Collection myWrappers;
  private final IIgnoreFileFilter myIgnoreFileFilter;

  public ImportDetails(File sourceLocation, String vendor, String releaseTag, String logMessage,
                       String moduleName, CvsEnvironment env,
                       Collection wrappers,
                       IIgnoreFileFilter ignoreFileFilter) {
    LOG.assertTrue(sourceLocation != null);
    myBaseImportDirectory = sourceLocation;
    myVendor = vendor;
    myReleaseTag = releaseTag;
    myLogMessage = logMessage;
    myModuleName = moduleName;
    myEnvironment = env;
    myWrappers = wrappers;
    myIgnoreFileFilter = ignoreFileFilter;
  }

  public void prepareCommand(ImportCommand result) {
    result.setVendorTag(myVendor);
    result.setReleaseTag(myReleaseTag);
    result.setModule(getModulePath());
    result.setLogMessage(myLogMessage);

    for (Iterator iterator = myWrappers.iterator(); iterator.hasNext();) {
      FileExtension fileExtension = (FileExtension)iterator.next();
      result.addWrapper("*." + fileExtension.getExtension(), fileExtension.getKeywordSubstitution().getSubstitution());
    }


    result.setKeywordSubstitutionOption(KeywordSubstitution.BINARY);
  }

  public int getTotalFilesInSourceDirectory() {
    return CvsOperation.calculateFilesIn(myBaseImportDirectory);
  }

  private String getModulePath() {
    String relativePath = FileUtil.getRelativePath(myBaseImportDirectory.getAbsoluteFile().getParentFile(),
            myBaseImportDirectory.getAbsoluteFile());
    return replaceBaseImportDirectoryNameToModuleNameIn(relativePath);
  }

  public String getModuleName() {
    return myModuleName;
  }

  private String replaceBaseImportDirectoryNameToModuleNameIn(String relativePath) {
    return myModuleName + relativePath.substring(myBaseImportDirectory.getName().length());
  }

  public File getWorkingDirectory() {
    return myBaseImportDirectory;
  }

  public CvsRootProvider getCvsRoot() {
    return CvsRootProvider.createOn(myBaseImportDirectory, myEnvironment);
  }

  public IIgnoreFileFilter getIgnoreFileFilter() {
    return myIgnoreFileFilter;
  }


}
