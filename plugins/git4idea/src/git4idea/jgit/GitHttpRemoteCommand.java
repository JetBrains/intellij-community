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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import git4idea.push.GitSimplePushResult;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.*;

/**
 * @author Kirill Likhodedov
 */
interface GitHttpRemoteCommand {

  String getUrl();
  void setUrl(String url);
  void run() throws GitAPIException, URISyntaxException, TransportException;
  void cleanup();
  GitHttpCredentialsProvider getCredentialsProvider();
  String getLogString();
  String getCommandString();

  class Fetch implements GitHttpRemoteCommand {

    private final Git myGit;
    private final GitHttpCredentialsProvider myCredentialsProvider;
    private String myUrl;
    private final List<RefSpec> myRefSpecs;

    Fetch(@NotNull Git git, @NotNull GitHttpCredentialsProvider credentialsProvider, @NotNull String url, @NotNull List<RefSpec> refSpecs) {
      myGit = git;
      myCredentialsProvider = credentialsProvider;
      myUrl = url;
      myRefSpecs = refSpecs;
    }

    @Override
    public void run() throws GitAPIException {
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
    public String getLogString() {
      return getCommandString();
    }

    @Override
    public String getCommandString() {
      return String.format("git fetch %s %s", myUrl, getRefspecsAsString(myRefSpecs));
    }

    static String getRefspecsAsString(@NotNull List<RefSpec> refSpecs) {
      return StringUtil.join(refSpecs, new Function<RefSpec, String>() {
        @Override
        public String fun(RefSpec spec) {
          return spec.toString();
        }
      }, " ");
    }

    @Override
    public void cleanup() {
    }
  }

  class Clone implements GitHttpRemoteCommand {

    private final File myTargetDirectory;
    private final GitHttpCredentialsProvider myCredentialsProvider;
    private String myUrl;
    @Nullable private Git myGit;

    Clone(@NotNull File targetDirectory, @NotNull GitHttpCredentialsProvider credentialsProvider, String url) {
      myTargetDirectory = targetDirectory;
      myCredentialsProvider = credentialsProvider;
      myUrl = url;
    }

    @Override
    public void run() throws GitAPIException {
      CloneCommand cloneCommand = Git.cloneRepository();
      cloneCommand.setDirectory(myTargetDirectory);
      cloneCommand.setURI(myUrl);
      cloneCommand.setCredentialsProvider(myCredentialsProvider);
      myGit = cloneCommand.call();
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
    public String getLogString() {
      return getCommandString();
    }

    @Override
    public String getCommandString() {
      return String.format("git clone %s %s", myUrl, myTargetDirectory.getPath());
    }

    @Override
    public void cleanup() {
      if (myTargetDirectory.exists()) {
        FileUtil.delete(myTargetDirectory);
      }
    }

    @Nullable
    public Git getGit() {
      return myGit;
    }
  }

  class Push implements GitHttpRemoteCommand {

    private final Git myGit;
    private final GitHttpCredentialsProvider myCredentialsProvider;
    private GitSimplePushResult myPushResult;
    private String myRemoteName;
    private String myUrl;
    private final List<RefSpec> myPushSpecs;

    Push(@NotNull Git git, @NotNull GitHttpCredentialsProvider credentialsProvider, @NotNull String remoteName, @NotNull String url, @NotNull List<RefSpec> pushSpecs) {
      myGit = git;
      myCredentialsProvider = credentialsProvider;
      myRemoteName = remoteName;
      myUrl = url;
      myPushSpecs = pushSpecs;
    }

    @Override
    public void run() throws InvalidRemoteException, URISyntaxException, org.eclipse.jgit.api.errors.TransportException {
      PushCommand pushCommand = myGit.push();
      pushCommand.setRemote(myRemoteName);
      pushCommand.setRefSpecs(myPushSpecs);
      pushCommand.setCredentialsProvider(myCredentialsProvider);
      
      /*
        Need to push to remote NAME (to let push update the remote reference), but to probably another URL.
        So constructing RemoteConfig based on the original config for the remote, but with other url.
        No need in fetch urls => just removing them.
        Remove all push urls (we don't support pushing to multiple urls anyway yet), leaving only single correct url.
        Then pass the url to the push command.
       */
      RemoteConfig rc = new RemoteConfig(myGit.getRepository().getConfig(), myRemoteName);
      List<URIish> uris = new ArrayList<URIish>(rc.getURIs());
      for (URIish uri : uris) {
        rc.removeURI(uri);
      }
      uris = new ArrayList<URIish>(rc.getPushURIs());
      for (URIish uri : uris) {
        rc.removePushURI(uri);
      }
      rc.addPushURI(new URIish(myUrl));
      
      Iterable<PushResult> results = call(pushCommand, rc);
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
    public String getLogString() {
      return String.format("git push %s (%s) %s", myRemoteName, myUrl, GitHttpRemoteCommand.Fetch.getRefspecsAsString(myPushSpecs));
    }

    @Override
    public String getCommandString() {
      return String.format("git push %s %s", myRemoteName, GitHttpRemoteCommand.Fetch.getRefspecsAsString(myPushSpecs));
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


    /*
      A copy-paste from org.eclipse.jgit.api.PushCommand#call with the following differences:
      1. Fields are not accessible, so they are substituted by getters, except for credentialsProvider, which we have stored as an instance field.
      2. checkCallable() won't fail (according to the PushCommand code), so it's safe to remove it.
      3. Actual push is performed via
           Transport.openAll(repo, remoteConfig, Transport.Operation.PUSH)
         instead of
           Transport.openAll(repo, remote, Transport.Operation.PUSH)
         where remoteConfig is passed to the method.
         Original code constructs the remoteConfig based on .git/config.
     */
    @NotNull
    private Iterable<PushResult> call(PushCommand pushCommand, RemoteConfig remoteConfig)
      throws JGitInternalException, InvalidRemoteException, org.eclipse.jgit.api.errors.TransportException
    {
      ArrayList<PushResult> pushResults = new ArrayList<PushResult>(3);

      List<RefSpec> refSpecs = pushCommand.getRefSpecs();
      Repository repo = pushCommand.getRepository();
      boolean force = pushCommand.isForce();
      int timeout = pushCommand.getTimeout();
      CredentialsProvider credentialsProvider = myCredentialsProvider;
      String receivePack = pushCommand.getReceivePack();
      boolean thin = pushCommand.isThin();
      boolean dryRun = pushCommand.isDryRun();
      String remote = pushCommand.getRemote();
      ProgressMonitor monitor = pushCommand.getProgressMonitor();

      try {
        if (refSpecs.isEmpty()) {
          RemoteConfig config = new RemoteConfig(repo.getConfig(), pushCommand.getRemote());
          refSpecs.addAll(config.getPushRefSpecs());
        }
        if (refSpecs.isEmpty()) {
          Ref head = repo.getRef(Constants.HEAD);
          if (head != null && head.isSymbolic()) {
            refSpecs.add(new RefSpec(head.getLeaf().getName()));
          }
        }

        if (force) {
          for (int i = 0; i < refSpecs.size(); i++) {
            refSpecs.set(i, refSpecs.get(i).setForceUpdate(true));
          }
        }

        final List<Transport> transports;
        transports = Transport.openAll(repo, remoteConfig, Transport.Operation.PUSH);
        for (final Transport transport : transports) {
          if (0 <= timeout) {
            transport.setTimeout(timeout);
          }
          transport.setPushThin(thin);
          if (receivePack != null) {
            transport.setOptionReceivePack(receivePack);
          }
          transport.setDryRun(dryRun);
          if (credentialsProvider != null) {
            transport.setCredentialsProvider(credentialsProvider);
          }

          final Collection<RemoteRefUpdate> toPush = transport
            .findRemoteRefUpdatesFor(refSpecs);

          try {
            PushResult result = transport.push(monitor, toPush);
            pushResults.add(result);
          }
          catch (TransportException e) {
            throw new org.eclipse.jgit.api.errors.TransportException(e.getMessage(), e);
          }
          finally {
            transport.close();
          }
        }
      }
      catch (URISyntaxException e) {
        throw new InvalidRemoteException(MessageFormat.format(
          JGitText.get().invalidRemote, remote));
      } catch (TransportException e) {
      			throw new org.eclipse.jgit.api.errors.TransportException(
      					e.getMessage(), e);
      }
      catch (NotSupportedException e) {
        throw new JGitInternalException(
          JGitText.get().exceptionCaughtDuringExecutionOfPushCommand,
          e);
      }
      catch (IOException e) {
        throw new JGitInternalException(
          JGitText.get().exceptionCaughtDuringExecutionOfPushCommand,
          e);
      }

      return pushResults;
    }
  }

  class LsRemote implements GitHttpRemoteCommand {

    private final Git myGit;
    private final GitHttpCredentialsProvider myCredentialsProvider;
    private String myUrl;
    private Collection<Ref> myResultRefs;

    public LsRemote(@NotNull Git git, @NotNull GitHttpCredentialsProvider credentialsProvider, @NotNull String url) {
      myGit = git;
      myCredentialsProvider = credentialsProvider;
      myUrl = url;
    }

    @Override
    public void run() throws InvalidRemoteException, TransportException {
      myResultRefs = call();
    }

    @Override
    public void cleanup() {
    }

    @Override
    public GitHttpCredentialsProvider getCredentialsProvider() {
      return myCredentialsProvider;
    }

    @Override
    public String getLogString() {
      return getCommandString();
    }

    @Override
    public String getCommandString() {
      return String.format("git ls-remote --heads %s ", myUrl);
    }

    @Override
    public String getUrl() {
      return myUrl;
    }

    @Override
    public void setUrl(@NotNull String url) {
      myUrl = url;
    }

    @NotNull
    public Collection<Ref> getRefs() {
      return myResultRefs == null ? Collections.<Ref>emptyList() : myResultRefs;
    }

    /*
      Copy-paste of org.eclipse.jgit.api.LsRemote#call with the following changes:
      1. More specific exceptions declaration.
      2. Use CredentialsProvider.
      3. We don't need --tags, we always need --heads.
     */
    private Collection<Ref> call() throws TransportException, InvalidRemoteException {
      try {
        Transport transport = Transport.open(myGit.getRepository(), myUrl);

        try {
          Collection<RefSpec> refSpecs = new ArrayList<RefSpec>(1);
          refSpecs.add(new RefSpec("refs/heads/*:refs/remotes/origin/*"));
          Collection<Ref> refs;
          Map<String, Ref> refmap = new HashMap<String, Ref>();
          transport.setCredentialsProvider(myCredentialsProvider);
          FetchConnection fc = transport.openFetch();
          try {
            refs = fc.getRefs();
            if (refSpecs.isEmpty()) {
              for (Ref r : refs) {
                refmap.put(r.getName(), r);
              }
            }
            else {
              for (Ref r : refs) {
                for (RefSpec rs : refSpecs) {
                  if (rs.matchSource(r)) {
                    refmap.put(r.getName(), r);
                    break;
                  }
                }
              }
            }
          }
          finally {
            fc.close();
          }
          return refmap.values();
        }
        catch (TransportException e) {
          throw new JGitInternalException(
            JGitText.get().exceptionCaughtDuringExecutionOfLsRemoteCommand,
            e);
        }
        finally {
          transport.close();
        }
      }
      catch (URISyntaxException e) {
        throw new InvalidRemoteException(MessageFormat.format(
          JGitText.get().invalidRemote, myUrl));
      }
      catch (NotSupportedException e) {
        throw new JGitInternalException(
          JGitText.get().exceptionCaughtDuringExecutionOfLsRemoteCommand,
          e);
      }
    }
  }
}


