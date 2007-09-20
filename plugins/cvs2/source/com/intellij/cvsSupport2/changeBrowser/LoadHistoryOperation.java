package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.LocalPathIndifferentOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsLog.RlogCommand;
import com.intellij.util.text.SyncDateFormat;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.log.LogInformation;

import java.text.SimpleDateFormat;
import java.util.*;

public class LoadHistoryOperation extends LocalPathIndifferentOperation {

  @NonNls private static final SyncDateFormat DATE_FORMAT = new SyncDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US));

  private static final Collection<String> ourDoNotSupportingSOptionServers = new HashSet<String>();

  private final String myModule;

  private final Date myDateFrom;
  private final Date myDateTo;
  private List<LogInformationWrapper> myLog;

  public LoadHistoryOperation(CvsEnvironment environment, String module,
                              @NotNull Date dateFrom,
                              @Nullable Date dateTo,
                              final List<LogInformationWrapper> log) {
    super(environment);
    myLog = log;
    myModule = module;
    myDateFrom = dateFrom;
    myDateTo = dateTo;
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    RlogCommand command = new RlogCommand();
    command.setModuleName(myModule);
    command.setHeadersOnly(false);
    command.setNoTags(false);
    command.setDateFrom(DATE_FORMAT.format(myDateFrom));
    if (myDateTo != null) {
      command.setDateTo(DATE_FORMAT.format(myDateTo));
    }

    if (ourDoNotSupportingSOptionServers.contains(root.getCvsRootAsString())) {
      command.setSuppressEmptyHeaders(false);
    }

    return command;
  }

  public static void doesNotSuppressEmptyHeaders(CvsEnvironment root) {
    ourDoNotSupportingSOptionServers.add(root.getCvsRootAsString());
  }

  @NonNls
  protected String getOperationName() {
    return "rlog";
  }

  public void fileInfoGenerated(Object info) {
    super.fileInfoGenerated(info);
    if (info instanceof LogInformation) {
      final LogInformation logInfo = (LogInformation)info;
      LogInformationWrapper wrapper = LogInformationWrapper.wrap(myEnvironment.getRepository(), logInfo);
      if (wrapper != null) {
        myLog.add(wrapper);
      }
    }
  }

  public boolean runInReadThread() {
    return false;
  }

  protected boolean runInExclusiveLock() {
    return false;
  }
}
