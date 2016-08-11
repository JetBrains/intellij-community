/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.GlobalOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class CvsOperation {
  private static final NotNullLazyValue<Map<String, String>> ourCvsEnvironment = new AtomicNotNullLazyValue<Map<String, String>>() {
    @NotNull
    @Override
    protected Map<String, String> compute() {
      Map<String, String> cvsEnv = new HashMap<>();

      Map<String, String> knownToCvs = EnvironmentUtil.getEnvironmentMap();
      @SuppressWarnings("SpellCheckingInspection") String[] toCvs = {
        "CVSIGNORE", "CVSWRAPPERS", "CVSREAD", "CVSREADONLYFS", "CVSUMASK",
        "CVSROOT", "CVSEDITOR", "EDITOR", "VISUAL", "PATH", "HOME", "HOMEPATH", "HOMEDRIVE", "CVS_RSH", "CVS_SERVER",
        "CVS_PASSFILE", "CVS_CLIENT_PORT", "CVS_PROXY_PORT", "CVS_RCMD_PORT", "CVS_CLIENT_LOG",
        "CVS_SERVER_SLEEP", "CVS_IGNORE_REMOTE_ROOT", "CVS_LOCAL_BRANCH_NUM", "COMSPEC", "TMPDIR",
        "CVS_PID", "COMSPEC", "CVS_VERIFY_TEMPLATE", "CVS_NOBASES", "CVS_SIGN_COMMITS", "CVS_VERIFY_CHECKOUTS"
      };
      for (String name : toCvs) {
        String value = knownToCvs.get(name);
        if (value != null) {
          cvsEnv.put(name, value);
        }
      }

      return cvsEnv;
    }
  };

  private final Collection<Runnable> myFinishActions = new ArrayList<>();

  public abstract void execute(CvsExecutionEnvironment executionEnvironment, boolean underReadAction) throws VcsException, CommandAbortedException;

  public abstract void appendSelfCvsRootProvider(@NotNull final Collection<CvsEnvironment> roots) throws CannotFindCvsRootException;

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
      options.setEnvVariables(ourCvsEnvironment.getValue());
    }
  }

  public int getFilesToProcessCount() {
    return CvsHandler.UNKNOWN_COUNT;
  }

  public static int calculateFilesIn(File file) {
    if (!file.isDirectory()) {
      return 1;
    }

    File[] subFiles = file.listFiles();
    if (subFiles == null) {
      subFiles = new File[0];
    }

    int result = 0;
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
