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
package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.GlobalOptions;

import java.io.File;
import java.util.*;

public abstract class CvsOperation {
  private final static String[] ourKnownToCvs = {"CVSIGNORE",
                                                 "CVSWRAPPERS",
                                                 "CVSREAD",
                                                 "CVSREADONLYFS",
                                                 "CVSUMASK",

                                                 "CVSROOT",
                                                 "CVSEDITOR",
                                                 "EDITOR",
                                                 "VISUAL",
                                                 "PATH",
                                                 //10
                                                 "HOME",
                                                 "HOMEPATH",
                                                 "HOMEDRIVE",
                                                 "CVS_RSH",
                                                 "CVS_SERVER",

                                                 "CVS_PASSFILE",
                                                 "CVS_CLIENT_PORT",
                                                 "CVS_PROXY_PORT",
                                                 "CVS_RCMD_PORT",
                                                 "CVS_CLIENT_LOG",

                                                 "CVS_SERVER_SLEEP",
                                                 "CVS_IGNORE_REMOTE_ROOT",
                                                 "CVS_LOCAL_BRANCH_NUM",
                                                 "COMSPEC",
                                                 "TMPDIR",

                                                 "CVS_PID",
                                                 "COMSPEC",
                                                 "CVS_VERIFY_TEMPLATE",
                                                 "CVS_NOBASES",
                                                 "CVS_SIGN_COMMITS",
    
                                                 "CVS_VERIFY_CHECKOUTS"
                                                };
  private final static Map<String, String> ourEnvironmentVariablesMap = new HashMap<String, String>();
  static {
    final Map<String, String> environmentProperties = EnvironmentUtil.getEnviromentProperties();
    for (String name : ourKnownToCvs) {
      final String value = environmentProperties.get(name);
      if (value != null) {
        ourEnvironmentVariablesMap.put(name, value);
      }
    }
  }

  private final Collection<Runnable> myFinishActions = new ArrayList<Runnable>();

  public abstract void execute(CvsExecutionEnvironment executionEnvironment, boolean underReadAction) throws VcsException, CommandAbortedException;

  public abstract void appendSelfCvsRootProvider(@NotNull final Collection<CvsRootProvider> roots) throws CannotFindCvsRootException;

  public void addFinishAction(Runnable action) {
    myFinishActions.add(action);
  }

  public void executeFinishActions() {
    for (final Runnable myFinishAction : myFinishActions) {
      myFinishAction.run();
    }
  }

  protected void modifyOptions(GlobalOptions options) {
    options.setUseGzip(CvsApplicationLevelConfiguration.getInstance().USE_GZIP);
    if (CvsApplicationLevelConfiguration.getInstance().SEND_ENVIRONMENT_VARIABLES_TO_SERVER) {
      options.setEnvVariables(ourEnvironmentVariablesMap);
    }
  }

  public int getFilesToProcessCount() {
    return CvsHandler.UNKNOWN_COUNT;
  }

  public static int calculateFilesIn(File file) {
    if (!file.isDirectory()) {
      return 1;
    }

    int result = 0;
    File[] subFiles = file.listFiles();
    if (subFiles == null) {
      subFiles = new File[0];
    }

    for (File subFile : subFiles) {
      result += calculateFilesIn(subFile);
    }

    return result;
  }

  public abstract String getLastProcessedCvsRoot();

  public boolean runInReadThread() {
    return true;
  }
}
