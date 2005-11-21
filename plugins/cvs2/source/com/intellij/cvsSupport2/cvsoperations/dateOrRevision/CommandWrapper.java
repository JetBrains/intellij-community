package com.intellij.cvsSupport2.cvsoperations.dateOrRevision;

import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.update.UpdateCommand;
import org.netbeans.lib.cvsclient.command.checkout.CheckoutCommand;
import org.netbeans.lib.cvsclient.command.checkout.ExportCommand;

/**
 * author: lesya
 */
public class CommandWrapper {
  private final Command myCommand;

  public CommandWrapper(Command command) {
    myCommand = command;
  }

  public void setUpdateByRevision(String revision){
    if (isCheckoutCommand()){
      asCheckoutCommand().setUpdateByRevisionOrTag(revision);
    } else if (isUpdateCommand()){
      asUpdateCommand().setUpdateByRevisionOrTag(revision);
    } else if (isExportCommand()) {
      asExportCommand().setUpdateByRevisionOrTag(revision);
    }
  }

  public void setUpdateByDate(String date){
    if (isCheckoutCommand()){
      asCheckoutCommand().setUpdateByDate(date);
    } else if (isUpdateCommand()){
      asUpdateCommand().setUpdateByDate(date);
    }
  }

  private UpdateCommand asUpdateCommand() {
    return ((UpdateCommand)myCommand);
  }

  private CheckoutCommand asCheckoutCommand() {
    return ((CheckoutCommand)myCommand);
  }

  private ExportCommand asExportCommand() {
    return ((ExportCommand)myCommand);
  }

  private boolean isUpdateCommand() {
    return myCommand instanceof UpdateCommand;
  }

  private boolean isCheckoutCommand() {
    return myCommand instanceof CheckoutCommand;
  }

  private boolean isExportCommand() {
    return myCommand instanceof ExportCommand;
  }

}
