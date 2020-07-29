// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.indexing.ID;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public final class NoAccessDuringPsiEvents {
  private static final Logger LOG = Logger.getInstance(NoAccessDuringPsiEvents.class);
  private static final Set<String> ourReportedTraces = new HashSet<>();

  public static void checkCallContext(@NotNull ID<?, ?> indexId) {
    checkCallContext("access index #" + indexId.getName());
  }

  public static void checkCallContext(@NotNull String contextDescription) {
    if (isInsideEventProcessing() && ourReportedTraces.add(DebugUtil.currentStackTrace())) {
      LOG.error("It's prohibited to " + contextDescription + " during event dispatching");
    }
  }

  public static boolean isInsideEventProcessing() {
    Application application = ApplicationManager.getApplication();
    if (!application.isWriteAccessAllowed()) return false;

    MessageBus bus = application.getMessageBus();
    return bus.hasUndeliveredEvents(VirtualFileManager.VFS_CHANGES) ||
           bus.hasUndeliveredEvents(PsiModificationTracker.TOPIC) ||
           bus.hasUndeliveredEvents(ProjectTopics.PROJECT_ROOTS);
  }
}
