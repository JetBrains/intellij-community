/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
