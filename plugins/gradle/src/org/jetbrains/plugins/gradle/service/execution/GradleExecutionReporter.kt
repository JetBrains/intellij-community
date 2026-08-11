// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.build.events.MessageEvent
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.annotations.CheckReturnValue
import org.jetbrains.plugins.gradle.issue.GradleIssueFailure
import org.jetbrains.plugins.gradle.statistics.GradleModelBuilderMessageCollector
import java.nio.file.Path

/**
 * Reports Gradle execution state to the build output and related infrastructure.
 *
 * The reporter is bound to a [GradleExecutionContext] and acts as the Gradle-specific reporting facade for execution code.
 * Low-level delivery details stay inside the implementation. Callers should report Gradle-domain events through this interface
 * instead of publishing external-system and build events directly.
 *
 * Context-level data such as project root, build environment, task id, and file positions derived from failure stacktraces
 * is resolved by the implementation at report time. Callers only provide domain data and optional presentation attributes.
 *
 * Use [GradleExecutionContext.reporter] to obtain the reporter for the current Gradle execution.
 */
@Experimental
@NonExtendable
interface GradleExecutionReporter {

  /**
   * Creates a configurable report for [failure]. The report is immutable; each `with...` method returns an updated report instance.
   */
  fun failure(failure: GradleIssueFailure): GradleExecutionFailureReport

  /**
   * Configures how a single [GradleIssueFailure] should be reported.
   */
  @NonExtendable
  @CheckReturnValue
  interface GradleExecutionFailureReport {

    /**
     * Defines the severity of a build message.
     */
    enum class Severity {
      ERROR,
      WARNING,
      INFO
    }

    /**
     * Sets the build message severity.
     * Defaults to [Severity.ERROR].
     */
    fun withSeverity(severity: Severity): GradleExecutionFailureReport

    /**
     * Marks the failure as internal.
     * Internal errors are hidden from UI and logged as errors.
     */
    fun withInternal(isInternal: Boolean): GradleExecutionFailureReport

    /**
     * Suppresses user-visible message events unless the `gradle.show.suppressed.failure.events` registry is enabled.
     * Suppressed failures can still be logged, reported to statistics, and shown as known build issues.
     */
    fun withSuppressed(isSuppressed: Boolean): GradleExecutionFailureReport

    /**
     * Sets the statistics group for Gradle failure reporting, or disables statistics reporting when `null`.
     */
    @ApiStatus.Internal
    fun withGroup(group: GradleModelBuilderMessageCollector.FailureGroup?): GradleExecutionFailureReport

    /**
     * Overrides the message event title. When omitted, the failure message is used as the title.
     */
    fun withTitle(title: @NlsSafe String?): GradleExecutionFailureReport

    /**
     * Overrides the report text. When omitted, the failure description or message is used as the text.
     */
    fun withText(text: @NlsSafe String?): GradleExecutionFailureReport

    /**
     * Sets a Gradle target directory used to create fallback navigation to a Gradle build file when the failure has no file position.
     */
    fun withTargetPath(targetPath: Path?): GradleExecutionFailureReport

    /**
     * Emits configured build events and logs for this failure.
     */
    fun report()
  }
}
