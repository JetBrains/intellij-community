// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.config;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsResultEx;
import com.intellij.cvsSupport2.connections.*;
import com.intellij.cvsSupport2.connections.login.CvsLoginWorker;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.PostCvsActivity;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetModulesListOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsErrors.ErrorMessagesProcessor;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsListenerWithProgress;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDateImpl;
import com.intellij.cvsSupport2.errorHandling.CvsException;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.openapi.cvsIntegration.CvsRepository;
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import org.netbeans.lib.cvsclient.CvsRoot;
import org.netbeans.lib.cvsclient.ValidRequestsExpectedException;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.progress.DummyProgressViewer;
import org.netbeans.lib.cvsclient.util.BugLog;

import java.io.IOException;
import java.util.List;

public class CvsRootConfiguration extends AbstractConfiguration implements CvsEnvironment, Cloneable {
  private static final Logger LOG = Logger.getInstance(CvsRootConfiguration.class);

  public String CVS_ROOT = "";
  public String PATH_TO_WORKING_FOLDER = "";

  public ProxySettings PROXY_SETTINGS = new ProxySettings();
  public ExtConfiguration EXT_CONFIGURATION = new ExtConfiguration();
  public SshSettings SSH_CONFIGURATION = new SshSettings();
  public SshSettings SSH_FOR_EXT_CONFIGURATION = new SshSettings();
  public LocalSettings LOCAL_CONFIGURATION = new LocalSettings();

  public DateOrRevisionSettings DATE_OR_REVISION_SETTINGS = new DateOrRevisionSettings();

  private static final String SEPARATOR = ":";
  private static final String AT = "@";

  public CvsRootConfiguration() {
    super("CvsRootConfiguration");
  }

  public CvsConnectionSettings createSettings() {
    return new IDEARootFormatter(this).createConfiguration();
  }

  @Override
  public IConnection createConnection(ReadWriteStatistics statistics) {
    return createSettings().createConnection(statistics);
  }

  @Override
  public String getCvsRootAsString() {
    return CVS_ROOT;
  }

  private static String createFieldByFieldCvsRoot(CvsRepository cvsRepository) {
    return createStringRepresentationOn(CvsMethod.getValue(cvsRepository.getMethod()),
                                        cvsRepository.getUser(),
                                        cvsRepository.getHost(),
                                        cvsRepository.getPort(),
                                        cvsRepository.getRepository());
  }

  public static String createStringRepresentationOn(CvsMethod method, String user, String host, int port, String repository) {
    if (method == CvsMethod.LOCAL_METHOD) {
      return SEPARATOR + method.getName() + SEPARATOR + repository;
    }
    final StringBuilder result = new StringBuilder();
    result.append(SEPARATOR);
    result.append(method.getName());
    result.append(SEPARATOR);
    result.append(user);
    result.append(AT);
    result.append(host);
    if (port > 0) {
      result.append(SEPARATOR);
      result.append(port);
    }
    else {
      result.append(SEPARATOR);
    }
    result.append(repository);
    return result.toString();
  }

  public String toString() {
    if (useBranch()) {
      return CvsBundle.message("cvs.root.configuration.on.branch.string.representation", getCvsRootAsString(),
                                DATE_OR_REVISION_SETTINGS.BRANCH);
    }
    else if (useDate()) {
      return CvsBundle.message("cvs.root.configuration.on.date.string.representation", getCvsRootAsString(),
                                DATE_OR_REVISION_SETTINGS.getDate());
    }
    else {
      return getCvsRootAsString();
    }
  }

  private boolean useDate() {
    return DATE_OR_REVISION_SETTINGS.USE_DATE && !DATE_OR_REVISION_SETTINGS.getDate().isEmpty();
  }

  private boolean useBranch() {
    return DATE_OR_REVISION_SETTINGS.USE_BRANCH && !DATE_OR_REVISION_SETTINGS.BRANCH.isEmpty();
  }

  public void testConnection(Project project) throws AuthenticationException, IOException {
    final IConnection connection = createSettings().createConnection(new ReadWriteStatistics());
    final ErrorMessagesProcessor errorProcessor = new ErrorMessagesProcessor();
    final CvsExecutionEnvironment cvsExecutionEnvironment =
      new CvsExecutionEnvironment(errorProcessor, CvsExecutionEnvironment.DUMMY_STOPPER, errorProcessor, PostCvsActivity.DEAF, project);
    final CvsResult result = new CvsResultEx();
    try {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        final GetModulesListOperation operation = new GetModulesListOperation(createSettings());

        final CvsRootProvider cvsRootProvider = operation.getCvsRootProvider();
        try {
          if (connection instanceof SelfTestingConnection) {
            ((SelfTestingConnection)connection).test(CvsListenerWithProgress.createOnProgress());
          }
          operation.execute(cvsRootProvider, cvsExecutionEnvironment, connection, DummyProgressViewer.INSTANCE);
        }
        catch (ValidRequestsExpectedException ex) {
          result.addError(new CvsException(ex, cvsRootProvider.getCvsRootAsString()));
        }
        catch (CommandException ex) {
          result.addError(new CvsException(ex.getUnderlyingException(), cvsRootProvider.getCvsRootAsString()));
        }
        catch (ProcessCanceledException ex) {
          result.setIsCanceled();
        }
        catch (BugLog.BugException e) {
          LOG.error(e);
        }
        catch (Exception e) {
          result.addError(new CvsException(e, cvsRootProvider.getCvsRootAsString()));
        }
      }, CvsBundle.message("operation.name.test.connection"), true, null);
      if (result.isCanceled()) throw new ProcessCanceledException();

      if (result.hasErrors()) {
        final VcsException vcsException = result.composeError();
        throw new AuthenticationException(vcsException.getLocalizedMessage(), vcsException.getCause());
      }
      final List<VcsException> errors = errorProcessor.getErrors();
      if (!errors.isEmpty()) {
        final VcsException firstError = errors.get(0);
        throw new AuthenticationException(firstError.getLocalizedMessage(), firstError);
      }
    }
    finally {
      connection.close();
    }
  }

  @Override
  public int hashCode() {
    int result = CVS_ROOT != null ? CVS_ROOT.hashCode() : 0;
    result = 31 * result + PROXY_SETTINGS.hashCode();
    result = 31 * result + EXT_CONFIGURATION.hashCode();
    result = 31 * result + SSH_CONFIGURATION.hashCode();
    result = 31 * result + SSH_FOR_EXT_CONFIGURATION.hashCode();
    result = 31 * result + LOCAL_CONFIGURATION.hashCode();
    result = 31 * result + DATE_OR_REVISION_SETTINGS.hashCode();
    return result;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof CvsRootConfiguration)) {
      return false;
    }
    final CvsRootConfiguration another = (CvsRootConfiguration)obj;

    return CVS_ROOT.equals(another.CVS_ROOT) &&
           DATE_OR_REVISION_SETTINGS.equals(another.DATE_OR_REVISION_SETTINGS) &&
           EXT_CONFIGURATION.equals(another.EXT_CONFIGURATION) &&
           SSH_CONFIGURATION.equals(another.SSH_CONFIGURATION) &&
           SSH_FOR_EXT_CONFIGURATION.equals(another.SSH_FOR_EXT_CONFIGURATION) &&
           LOCAL_CONFIGURATION.equals(another.LOCAL_CONFIGURATION) &&
           PROXY_SETTINGS.equals(another.PROXY_SETTINGS);
  }

  @Override
  public CvsLoginWorker getLoginWorker(Project project) {
    return createSettings().getLoginWorker(project);
  }

  @Override
  public RevisionOrDate getRevisionOrDate() {
    return RevisionOrDateImpl.createOn(DATE_OR_REVISION_SETTINGS);
  }

  @Override
  public String getRepository() {
    return createSettings().getRepository();
  }

  @Override
  public CvsRoot getCvsRoot() {
    return createSettings().getCvsRoot();
  }

  @Override
  public boolean isValid() {
    return createSettings().isValid();
  }

  public CvsRepository createCvsRepository() {
    final CvsConnectionSettings settings = createSettings();
    return new CvsRepository(settings.getCvsRootAsString(), (settings.METHOD == null) ? "" : settings.METHOD.getName(), settings.USER,
                             settings.HOST, settings.REPOSITORY, settings.PORT, DATE_OR_REVISION_SETTINGS);

  }

  public static CvsRootConfiguration createOn(CvsRepository repository) {
    final CvsRootConfiguration result = CvsApplicationLevelConfiguration.createNewConfiguration(CvsApplicationLevelConfiguration.getInstance());
    result.DATE_OR_REVISION_SETTINGS.updateFrom(repository.getDateOrRevision());
    result.CVS_ROOT = createFieldByFieldCvsRoot(repository);
    return result;
  }

  @Override
  public CvsRootConfiguration clone() {
    try {
      final CvsRootConfiguration result = (CvsRootConfiguration)super.clone();
      result.DATE_OR_REVISION_SETTINGS = DATE_OR_REVISION_SETTINGS.clone();
      result.PROXY_SETTINGS = PROXY_SETTINGS.clone();
      result.EXT_CONFIGURATION = EXT_CONFIGURATION.clone();
      result.SSH_CONFIGURATION = SSH_CONFIGURATION.clone();
      result.SSH_FOR_EXT_CONFIGURATION = SSH_FOR_EXT_CONFIGURATION.clone();
      result.LOCAL_CONFIGURATION = LOCAL_CONFIGURATION.clone();
      return result;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public CommandException processException(CommandException t) {
    return createSettings().processException(t);
  }

  @Override
  public boolean isOffline() {
    return createSettings().isOffline();
  }
}
