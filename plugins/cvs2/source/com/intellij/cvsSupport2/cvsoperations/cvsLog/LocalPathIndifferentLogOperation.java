package com.intellij.cvsSupport2.cvsoperations.cvsLog;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.LocalPathIndifferentOperation;
import com.intellij.cvsSupport2.cvsoperations.common.LocalPathIndifferentOperationHelper;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.BranchesProvider;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsHelper;
import com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.ConstantLocalFileReader;
import com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.DeafAdminWriter;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.log.LogCommand;
import org.netbeans.lib.cvsclient.command.log.LogInformation;
import org.netbeans.lib.cvsclient.file.ILocalFileReader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class LocalPathIndifferentLogOperation extends LocalPathIndifferentOperation implements BranchesProvider {

  private List<LogInformation> myLogInformationList = new ArrayList<LogInformation>();


  private final LocalPathIndifferentOperationHelper myHelper;

  private LocalPathIndifferentLogOperation(CvsEnvironment environment,
                                           LocalPathIndifferentOperationHelper helper) {
    super(helper.getAdminReader(), new DeafAdminWriter(), environment);
    myHelper = helper;
  }

  public LocalPathIndifferentLogOperation(CvsEnvironment environment) {
    this(environment, new LocalPathIndifferentOperationHelper());
  }

  public LocalPathIndifferentLogOperation(File file) {
    this(CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(file.getParentFile()));
    addIOFile(file);
  }

  public LocalPathIndifferentLogOperation(File[] files) {
    this(CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(files[0].getParentFile()));
    for (File file : files) {
      addIOFile(file);
    }
  }

  private void addIOFile(File file) {
    String repository = CvsUtil.getRepositoryFor(file.getParentFile());
    addFile(new File(repository, file.getName()));
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    LogCommand command = new LogCommand();
    myHelper.addFilesTo(command);
    return command;
  }

  public void fileInfoGenerated(Object info) {
    super.fileInfoGenerated(info);
    if (info instanceof LogInformation) {
      myLogInformationList.add((LogInformation)info);
    }
  }

  @Nullable public LogInformation getFirstLogInformation() {
    if (!myLogInformationList.isEmpty()) {
      return myLogInformationList.get(0);
    } else {
      return null;
    }
  }

  public List<LogInformation> getLogInformationList() {
    return myLogInformationList;
  }

  protected ILocalFileReader createLocalFileReader() {
    return ConstantLocalFileReader.FOR_EXISTING_FILE;
  }

  public void addFile(File file) {
    myHelper.addFile(file);
  }

  public Collection<String> getAllBranches() {
    return TagsHelper.getAllBranches(myLogInformationList);
  }

  public Collection<CvsRevisionNumber> getAllRevisions() {
    return TagsHelper.getAllRevisions(myLogInformationList);
  }

  protected String getOperationName() {
    return "log";
  }

  public boolean runInReadThread() {
    return false;
  }
}
