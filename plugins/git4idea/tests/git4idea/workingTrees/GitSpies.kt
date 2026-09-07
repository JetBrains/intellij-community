// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import git4idea.commands.Git
import git4idea.commands.GitLineHandler
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.doCallRealMethod
import org.mockito.Mockito.mockingDetails
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Keeps the [Git] spies of a test class, and clears the calls that they recorded.
 *
 * A spy records every call, which is what makes `verify` work. A `Git` call takes a `GitRepository`,
 * which holds the project, so a recorded call keeps a disposed project alive and the leak check fails.
 * Mockito holds a recorded call in two places, and both of them belong to the spy's invocation
 * container: the list of the recorded calls, and the last call, which Mockito keeps so that
 * `when(spy.foo())` can work. A thread local per thread points to that same container, so it is enough
 * to clear the container itself. That needs no cleanup per thread, because would not be reliable: the
 * coroutine pool grows on demand, so a test cannot reach every worker thread.
 */
internal object GitSpies {
  private val spies = CopyOnWriteArrayList<Git>()

  /** Registers [spy] for the cleanup and returns it. */
  fun register(spy: Git): Git {
    spies.add(spy)
    return spy
  }

  /**
   * Clears the calls that the registered spies recorded.
   *
   * Call this from `@AfterAll` only. A spy stays the registered service until the test disposable
   * restores the real one, and the platform keeps calling the service from a background thread. Such
   * a call would record the project again.
   */
  fun clearRecordedCalls() {
    for (spy in spies) {
      // A stub also keeps the call that matched it last, and that call holds the project. So every stub
      // matches once more with null arguments. The real method then gets a null argument and can throw,
      // which is expected and harmless here.
      for (stubbing in mockingDetails(spy).stubbings) {
        val method = stubbing.invocation.method
        try {
          method.invoke(spy, *arrayOfNulls<Any?>(method.parameterCount))
        }
        catch (_: Throwable) {
        }
      }
      // Records a call whose only argument is null, so the last recorded call holds no project.
      // The stub matches a null handler only, so a real call keeps its behavior.
      doCallRealMethod().`when`(spy).runCommand(isNull<GitLineHandler>())
      clearInvocations(spy)
    }
    spies.clear()
  }
}
