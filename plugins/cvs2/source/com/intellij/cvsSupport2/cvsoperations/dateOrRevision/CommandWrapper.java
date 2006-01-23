package com.intellij.cvsSupport2.cvsoperations.dateOrRevision;

import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.checkout.CheckoutCommand;
import org.netbeans.lib.cvsclient.command.checkout.ExportCommand;
import org.netbeans.lib.cvsclient.command.update.UpdateCommand;

/**
 * author: lesya
 */
public class CommandWrapper {
  private final Command myCommand;

  public CommandWrapper(Command command) {
    myCommand = command;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void setUpdateByRevisionOrDate(String revision, final String date) {
    if (isCheckoutCommand()) {
      asCheckoutCommand().setUpdateByRevisionOrTag(revision);
      asCheckoutCommand().setUpdateByDate(date);
    }
    else if (isUpdateCommand()) {
      asUpdateCommand().setUpdateByRevisionOrTag(revision);
      asUpdateCommand().setUpdateByDate(date);
    }
    else if (isExportCommand()) {
      asExportCommand().setUpdateByRevisionOrTag(revision == null && date == null ? "HEAD" : revision);
      asExportCommand().setUpdateByDate(date);
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
