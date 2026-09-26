// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.util.messages.Topic;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

public interface GitAuthenticationListener {

  Topic<GitAuthenticationListener> GIT_AUTHENTICATION_SUCCESS = Topic.create("Authentication succeeded", GitAuthenticationListener.class);

  void authenticationSucceeded(@NotNull GitRepository repository, @NotNull GitRemote remote);
}
