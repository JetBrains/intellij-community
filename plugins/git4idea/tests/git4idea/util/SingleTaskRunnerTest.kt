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

internal class SingleTaskRunnerTest {
  @Test
  fun `test inactive before start`() = timeoutRunBlocking {
    withRunner(task = { }) {
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
    withRunner({ ran = true }) {
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
    withRunner({
                 runAllowed.first { it }
                 counter++
               }) {
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
    noinline task: suspend () -> Unit,
    consumer: SingleTaskRunner.() -> Unit,
  ) {
    val cs = childScope("BG runner")
    SingleTaskRunner(cs, task).also(consumer)
    cs.cancel()
  }
}