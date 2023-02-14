// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.status;

import com.intellij.util.messages.Topic;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

public interface GitRefreshListener {
  Topic<GitRefreshListener> TOPIC = Topic.create("GitRefreshListener Progress", GitRefreshListener.class);

  default void repositoryUpdated(@NotNull GitRepository repository) {}

  default void progressStarted() {}

  default void progressStopped() {}
}
