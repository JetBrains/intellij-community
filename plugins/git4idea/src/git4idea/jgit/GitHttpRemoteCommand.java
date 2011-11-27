/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.jgit;

import com.intellij.openapi.util.io.FileUtil;
import git4idea.push.GitSimplePushResult;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
interface GitHttpRemoteCommand {

  String getUrl();
  void setUrl(String url);
  void run() throws InvalidRemoteException;
  void cleanup();
  GitHttpCredentialsProvider getCredentialsProvider();

  class Fetch implements GitHttpRemoteCommand {

    private final Git myGit;
    private final GitHttpCredentialsProvider myCredentialsProvider;
    private String myUrl;
    private final List<RefSpec> myRefSpecs;

    Fetch(@NotNull Git git, @NotNull GitHttpCredentialsProvider credentialsProvider, String url, List<RefSpec> refSpecs) {
      myGit = git;
      myCredentialsProvider = credentialsProvider;
      myUrl = url;
      myRefSpecs = refSpecs;
    }

    @Override
    public void run() throws InvalidRemoteException {
      FetchCommand fetchCommand = myGit.fetch();
      fetchCommand.setRemote(myUrl);
      fetchCommand.setRefSpecs(myRefSpecs);
      fetchCommand.setCredentialsProvider(myCredentialsProvider);
      fetchCommand.call();
    }

    @Override
    public void setUrl(@NotNull String url) {
      myUrl = url;
    }
    
    @Override
    public String getUrl() {
      return myUrl;
    }

    @Override
    public GitHttpCredentialsProvider getCredentialsProvider() {
      return myCredentialsProvider;
    }

    @Override
    public void cleanup() {
    }
  }

  class Clone implements GitHttpRemoteCommand {

    private final File myTargetDirectory;
    private final GitHttpCredentialsProvider myCredentialsProvider;
    private String myUrl;

    Clone(@NotNull File targetDirectory, @NotNull GitHttpCredentialsProvider credentialsProvider, String url) {
      myTargetDirectory = targetDirectory;
      myCredentialsProvider = credentialsProvider;
      myUrl = url;
    }

    @Override
    public void run() throws InvalidRemoteException {
      CloneCommand cloneCommand = Git.cloneRepository();
      cloneCommand.setDirectory(myTargetDirectory);
      cloneCommand.setURI(myUrl);
      cloneCommand.setCredentialsProvider(myCredentialsProvider);
      cloneCommand.call();
    }

    @Override
    public void setUrl(@NotNull String url) {
      myUrl = url;
    }

    @Override
    public String getUrl() {
      return myUrl;
    }
    
    @Override
    public GitHttpCredentialsProvider getCredentialsProvider() {
      return myCredentialsProvider;
    }

    @Override
    public void cleanup() {
      if (myTargetDirectory.exists()) {
        FileUtil.delete(myTargetDirectory);
      }
    }
  }

  class Push implements GitHttpRemoteCommand {

    private final Git myGit;
    private final GitHttpCredentialsProvider myCredentialsProvider;
    private GitSimplePushResult myPushResult;
    private String myUrl;
    private final List<RefSpec> myRefSpecs;

    Push(@NotNull Git git, @NotNull GitHttpCredentialsProvider credentialsProvider, String url, List<RefSpec> refSpecs) {
      myGit = git;
      myCredentialsProvider = credentialsProvider;
      myUrl = url;
      myRefSpecs = refSpecs;
    }

    @Override
    public void run() throws InvalidRemoteException {
      PushCommand pushCommand = myGit.push();
      if (myUrl != null) {
        pushCommand.setRemote(myUrl);
        pushCommand.setRefSpecs(myRefSpecs);
      }
      pushCommand.setCredentialsProvider(myCredentialsProvider);
      Iterable<PushResult> results = pushCommand.call();
      myPushResult = analyzeResults(results);
    }

    @Override
    public void setUrl(@NotNull String url) {
      myUrl = url;
    }

    @Override
    public String getUrl() {
      return myUrl;
    }

    @Override
    public GitHttpCredentialsProvider getCredentialsProvider() {
      return myCredentialsProvider;
    }
    
    @Override
    public void cleanup() {
    }

    @Nullable
    GitSimplePushResult getResult() {
      return myPushResult;
    }

    @NotNull
    private static GitSimplePushResult analyzeResults(@NotNull Iterable<PushResult> results) {
      Collection<String> rejectedBranches = new ArrayList<String>();
      StringBuilder errorReport = new StringBuilder();

      for (PushResult result : results) {
        for (RemoteRefUpdate update : result.getRemoteUpdates()) {
          switch (update.getStatus()) {
            case REJECTED_NONFASTFORWARD:
              rejectedBranches.add(update.getSrcRef());
              // no break: add reject to the output
            case NON_EXISTING:
            case REJECTED_NODELETE:
            case REJECTED_OTHER_REASON:
            case REJECTED_REMOTE_CHANGED:
              errorReport.append(update.getSrcRef() + ": " + update.getStatus() + "<br/>");
            default:
              // on success do nothing
          }
        }
      }

      if (!rejectedBranches.isEmpty()) {
        return GitSimplePushResult.reject(rejectedBranches);
      }
      else if (errorReport.toString().isEmpty()) {
        return GitSimplePushResult.success();
      }
      else {
        return GitSimplePushResult.error(errorReport.toString());
      }
    }
  }

}


