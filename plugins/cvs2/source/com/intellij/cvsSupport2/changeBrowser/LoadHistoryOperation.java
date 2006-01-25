package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.LocalPathIndifferentOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsLog.RlogCommand;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.log.LogInformation;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;

public class LoadHistoryOperation extends LocalPathIndifferentOperation {

  private static final Collection<String> ourDoNotSupportingSOptionServers = new HashSet<String>();

  private final String myModule;

  private final CvsHistoryCacheElement myCacheElement;
  private Date myDateFrom;
  private final Date myDateTo;

  public LoadHistoryOperation(CvsEnvironment environment, String module, final CvsHistoryCacheElement builder, @NotNull Date lastLogDate) {
    super(environment);
    myModule = module;
    myCacheElement = builder;
    myDateTo = lastLogDate;
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    RlogCommand command = new RlogCommand();
    command.setModuleName(myModule);
    command.setHeadersOnly(false);
    command.setNoTags(true);
    command.setDateFrom(myDateFrom);
    command.setDateTo(myDateTo);

    if (ourDoNotSupportingSOptionServers.contains(root.getCvsRootAsString())) {
      command.setSuppressEmptyHeaders(false);
    }

    return command;
  }

  public static void doesNotSuppressEmptyHeaders(CvsConnectionSettings root) {
    ourDoNotSupportingSOptionServers.add(root.getCvsRootAsString());
  }

  @NonNls
  protected String getOperationName() {
    return "rlog";
  }

  public void fileInfoGenerated(Object info) {
    super.fileInfoGenerated(info);
    if (info instanceof LogInformation) {
      myCacheElement.saveLogInformation((LogInformation)info);
    }
  }

  public void setDateFrom(final Date date) {
    myDateFrom = date;
  }
}
