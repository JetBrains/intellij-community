// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update;

import com.intellij.util.Consumer;
import com.intellij.util.messages.Topic;

import java.util.Set;

public interface UpdatedFilesListener extends Consumer<Set<String>> {

  @Topic.ProjectLevel
  Topic<UpdatedFilesListener> UPDATED_FILES = new Topic<>("AbstractCommonUpdateAction.UpdatedFiles", UpdatedFilesListener.class);
}
