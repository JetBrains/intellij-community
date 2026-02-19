// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
@Name("com.intellij.platform.CheckCanceledEvent")
@Label("ProgressManager.checkCanceled")
@Description("Event indicating that progress cancellation was checked")
@Category("IntelliJ Platform")
@StackTrace(false)
class CheckCanceledEvent extends Event {

  @Label("Non-cancellable section")
  @Description("True if the check for cancellation was performed in a non-cancellable context")
  private final boolean nonCancellable;

  @Label("Has progress indicator")
  @Description("True if ProgressIndicator was installed during the check for cancellation")
  private final boolean hasProgressIndicator;

  @Label("Has context job")
  @Description("True if context Job")
  private final boolean hasContextJob;

  @Label("NONE behavior is enabled")
  @Description("True if runs under CheckCanceledBehavior.NONE")
  private final boolean hasNoneBehavior;

  @Label("ONLY_HOOKS behavior is enabled")
  @Description("True if runs under CheckCanceledBehavior.ONLY_HOOKS")
  private final boolean hasOnlyHooksBehavior;

  @Label("Is canceled")
  @Description("True if this checkCanceled will throw a ProcessCanceledException")
  private final boolean cancelled;


  private CheckCanceledEvent(boolean nonCancellable,
                             boolean hasProgressIndicator,
                             boolean hasContextJob,
                             boolean hasNoneBehavior,
                             boolean hasOnlyHooksBehavior,
                             boolean cancelled) {
    this.nonCancellable = nonCancellable;
    this.hasProgressIndicator = hasProgressIndicator;
    this.hasContextJob = hasContextJob;
    this.hasNoneBehavior = hasNoneBehavior;
    this.hasOnlyHooksBehavior = hasOnlyHooksBehavior;
    this.cancelled = cancelled;
  }

  static void commit(boolean nonCancellable,
                     boolean hasProgressIndicator,
                     boolean hasContextJob,
                     boolean hasNoneBehavior,
                     boolean hasOnlyHooksBehavior,
                     boolean cancelled) {
    CheckCanceledEvent event = new CheckCanceledEvent(nonCancellable,
                                                      hasProgressIndicator,
                                                      hasContextJob,
                                                      hasNoneBehavior,
                                                      hasOnlyHooksBehavior,
                                                      cancelled);
    if (event.isEnabled() && event.shouldCommit()) {
      event.commit();
    }
  }
}