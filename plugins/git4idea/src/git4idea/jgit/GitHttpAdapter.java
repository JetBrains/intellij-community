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

import com.intellij.openapi.diagnostic.Logger;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.update.GitFetchResult;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * Handles remote operations over HTTP via JGit library.
 *
 * @author Kirill Likhodedov
 */
public final class GitHttpAdapter {

  private static final Logger LOG = Logger.getInstance(GitHttpAdapter.class);

  /**
   * Nothing more than Runnable with exceptions.
   */
  private interface MyRunnable {
    void run() throws IOException, InvalidRemoteException;
  }

  private GitHttpAdapter() {
  }

  /**
   * Fetches the given remote in the given Git repository.
   * Asks username and password if needed.
   */
  @NotNull
  public static GitFetchResult fetch(@NotNull final GitRepository repository, @NotNull final GitRemote remote)  {
    GitFetchResult.Type resultType;
    try {
      final Git git = convertToGit(repository);
      final GitHttpCredentialsProvider provider = new GitHttpCredentialsProvider(repository.getProject(), remote);
      resultType = callWithAuthRetry(new MyRunnable() {
        @Override
        public void run() throws InvalidRemoteException {
          FetchCommand fetchCommand = git.fetch();
          fetchCommand.setRemote(remote.getName());
          fetchCommand.setCredentialsProvider(provider);
          fetchCommand.call();
        }
      }, provider);
    } catch (IOException e) {
      LOG.info("Exception while fetching " + remote + " in " + repository.toLogString(), e);
      return GitFetchResult.error(e);
    }
    catch (InvalidRemoteException e) {
      LOG.info("Exception while fetching " + remote + " in " + repository.toLogString(), e);
      return GitFetchResult.error(e);
    }
    return new GitFetchResult(resultType);
  }

  /**
   * Calls the given runnable.
   * If user cancels the authentication dialog, returns.
   * If user enters incorrect data, he has 2 more attempts to go before failure.
   */
  private static GitFetchResult.Type callWithAuthRetry(@NotNull MyRunnable command, GitHttpCredentialsProvider provider) throws InvalidRemoteException, IOException {
    for (int i = 0; i < 3; i++) {
      try {
        command.run();
        return GitFetchResult.Type.SUCCESS;
      } catch (JGitInternalException e) {
        if (!authError(e)) {
          throw e;
        } else if (provider.wasCancelled()) {  // if user cancels the dialog, just return
          return GitFetchResult.Type.CANCELLED;
        }
      }
    }
    return GitFetchResult.Type.NOT_AUTHORIZED;
  }
  
  private static boolean authError(@NotNull JGitInternalException e) {
    Throwable cause = e.getCause();
    return (cause instanceof TransportException && cause.getMessage().contains("not authorized"));
  }

  public static boolean isHttpUrl(@NotNull String url) {
    return url.startsWith("http");
  }

  /**
   * Converts {@link GitRepository} to JGit's {@link Repository}.
   */
  @NotNull
  private static Repository convert(@NotNull GitRepository repository) throws IOException {
    FileRepositoryBuilder builder = new FileRepositoryBuilder();
    return builder.setGitDir(new File(repository.getRoot().getPath(), ".git"))
    .readEnvironment() // scan environment GIT_* variables
    .findGitDir()     // scan up the file system tree
    .build();
  }

  /**
   * Converts {@link GitRepository} to JGit's {@link Git} object.
   */
  private static Git convertToGit(@NotNull GitRepository repository) throws IOException {
    return Git.wrap(convert(repository));
  }

}
