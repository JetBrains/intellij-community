// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history;

public class CollectingHistorySessionConsumer extends VcsAppendableHistoryPartnerAdapter implements VcsHistorySessionConsumer {
  @Override
  public void finished() {
  }
}
