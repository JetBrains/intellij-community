// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface VcsHistorySessionConsumer extends VcsAppendableHistorySessionPartner {
  void finished();
}
