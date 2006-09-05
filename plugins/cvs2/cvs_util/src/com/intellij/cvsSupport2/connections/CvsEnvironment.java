package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import org.netbeans.lib.cvsclient.CvsRoot;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.IConnection;

public interface CvsEnvironment {
  IConnection createConnection(ReadWriteStatistics statistics);

  String getCvsRootAsString();

  boolean login(ModalityContext executor);

  RevisionOrDate getRevisionOrDate();

  String getRepository();

  CvsRoot getCvsRoot();

  boolean isValid();

  CommandException processException(CommandException t);

  boolean isOffline();
}
