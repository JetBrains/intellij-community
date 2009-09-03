package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.LocalPathIndifferentOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsLog.RlogCommand;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.command.Command;

import java.util.Collection;

public class GetAllBranchesOperation extends LocalPathIndifferentOperation implements BranchesProvider {
  private final Collection<String> myTags = new HashSet<String>();
  @NonNls private final static String START = "symbolic names:";
  @NonNls private final static String END = "keyword substitution:";
  private boolean myIsInBranchesMode = false;


  public GetAllBranchesOperation(CvsEnvironment environment) {
    super(environment);
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    final RlogCommand command = new RlogCommand();
    // TODO[yole]: it would be best to implement smarter handling similar to LoadHistoryOperation, but it's too cumbersome without a major refactoring
    command.setSuppressEmptyHeaders(false);  // see IDEADEV-14276
    return command;
  }

  public void messageSent(String message, final byte[] byteMessage, boolean error, boolean tagged) {
    if (error) return;
    if (tagged) return;
    if (message.startsWith(START)) {
      myIsInBranchesMode = true;
      return;
    }

    if (message.startsWith(END)) {
      myIsInBranchesMode = false;
      return;
    }

    if (myIsInBranchesMode) {
      String trimmedMessage = message.trim();
      int lastIndex = trimmedMessage.indexOf(":");
      if (lastIndex >= 0) {
        myTags.add(trimmedMessage.substring(0, lastIndex));
      }
    }
  }

  public Collection<String> getAllBranches(){
    return myTags;
  }

  public Collection<CvsRevisionNumber> getAllRevisions() {
    return null;
  }

  protected String getOperationName() {
    return "rlog";
  }
}
