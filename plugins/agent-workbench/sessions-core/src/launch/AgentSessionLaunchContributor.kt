// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.launch

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Multi-extension hook for adding env variables / CLI args to a session's launch spec.
 * Fires for **both** new sessions (via `createNewSession`) and resumed ones (via
 * `resolveResume`), regardless of which open path the user took (chat tree click,
 * rename rebind, prompt-launch resume, etc.). All registered contributors are applied
 * in order; later ones see the result of earlier ones.
 *
 * Distinct from [AgentSessionLaunchSpecAugmenter]:
 *  - That EP is single-extension and intended for *project*-level launch config
 *    (working dir, shell wrapper).
 *  - This EP is multi-extension and intended for the (project, provider) — and for
 *    resume launches additionally (sessionId) — to plug per-session decorations.
 *    Examples: [AwbMcpConfigContributor] regenerates the merged `--mcp-config` file
 *    with the current IDE URL on every launch; the container plugin's contributor
 *    rebinds resumed threads to active container sessions.
 *
 * `sessionId` is `null` for new launches (no thread exists yet) and the runtime thread
 * id for resumes. Contributors that only make sense on resume (e.g. binding to an
 * existing container session) should early-return when it is null.
 *
 * Contributors should be cheap and side-effect-free: they're called every time a
 * session is launched, including UI-driven reopens.
 */
fun interface AgentSessionLaunchContributor {
  /**
   * Returns a (possibly augmented) launch spec. Return [launchSpec] unchanged when the
   * contributor doesn't apply to this launch.
   */
  suspend fun contribute(
    projectPath: String,
    provider: AgentSessionProvider,
    sessionId: String?,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec
}

private class AgentSessionLaunchContributorRegistry

private val LOG = logger<AgentSessionLaunchContributorRegistry>()

private val AGENT_SESSION_LAUNCH_CONTRIBUTOR_EP: ExtensionPointName<AgentSessionLaunchContributor> =
  ExtensionPointName("com.intellij.agent.workbench.sessionLaunchContributor")

object AgentSessionLaunchContributors {
  suspend fun applyAll(
    projectPath: String,
    provider: AgentSessionProvider,
    sessionId: String?,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec {
    val contributors = AGENT_SESSION_LAUNCH_CONTRIBUTOR_EP.extensionList
    if (contributors.isEmpty()) return launchSpec
    var result = launchSpec
    for (contributor in contributors) {
      result = try {
        contributor.contribute(projectPath = projectPath, provider = provider, sessionId = sessionId, launchSpec = result)
      }
      catch (e: CancellationException) {
        // Coroutine cancellation must propagate — never log-and-swallow.
        throw e
      }
      catch (e: Exception) {
        // Don't let a broken plugin contributor poison the launch: log and use the
        // pre-contributor result. `Error` (OOM, StackOverflow, …) is intentionally
        // *not* caught here — those are JVM-fatal and should crash up the stack.
        // Logged at `error` (not `warn`) so a failing extension is visible by
        // default in `idea.log`; previously the `warn` masked classloading failures
        // like the EP plugin XML pointing at a missing class name.
        LOG.error("Launch contributor ${contributor::class.java.name} failed; skipping", e)
        currentCoroutineContext().ensureActive()
        result
      }
    }
    return result
  }
}
