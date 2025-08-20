package git4idea.util

import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class DebouncedTaskRunnerTest {
  @Test
  fun `test inactive before start`() = timeoutRunBlocking {
    withRunner(awaitDebounce = { },
               task = { }) {
      request()
      assertThrows<TimeoutCancellationException> {
        withTimeout(100) {
          awaitNotBusy()
        }
      }
    }
  }

  @Test
  fun `test executed`() = timeoutRunBlocking {
    var ran = false
    withRunner(awaitDebounce = { },
               task = { ran = true }) {
      start()

      request()
      awaitNotBusy()
      assertTrue(ran)
    }
  }

  @Test
  fun `test execution debounced`() = timeoutRunBlocking {
    var counter = 0
    val runAllowed = MutableStateFlow(false)
    withRunner(awaitDebounce = { runAllowed.first { it } },
               task = { counter++ }) {
      start()

      repeat(10) {
        request()
      }
      runAllowed.value = true
      awaitNotBusy()
      assertEquals(1, counter)

      runAllowed.value = false
      request()
      assertThrows<TimeoutCancellationException> {
        withTimeout(100) {
          awaitNotBusy()
        }
      }
      assertEquals(1, counter)
      runAllowed.value = true
      awaitNotBusy()
      assertEquals(2, counter)
    }
  }

  private inline fun CoroutineScope.withRunner(
    noinline awaitDebounce: suspend () -> Unit,
    noinline task: suspend () -> Unit,
    consumer: DebouncedTaskRunner.() -> Unit,
  ) {
    val cs = childScope("BG runner")
    DebouncedTaskRunner(cs, awaitDebounce, task).also(consumer)
    cs.cancel()
  }
}