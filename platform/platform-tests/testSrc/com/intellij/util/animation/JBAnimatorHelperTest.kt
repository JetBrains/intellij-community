package com.intellij.util.animation

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS.WINDOWS

@TestApplication
@Suppress("SuspiciousPackagePrivateAccess")
internal class JBAnimatorHelperTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @BeforeEach
  fun checkClassLoader() {
    assertThat(JBAnimatorHelper::class.java.classLoader).isSameAs(javaClass.classLoader)
  }

  @Test
  fun `duplicate requests and cancellations do not change the timer twice`() {
    val library = RecordingWinMM()
    val helper = JBAnimatorHelper(library)
    val requestor = JBAnimator(disposable)
    helper.request(requestor)
    helper.request(requestor)
    helper.cancel(requestor)
    helper.cancel(requestor)
    assertThat(library.calls).containsExactly("begin:1", "end:1")
  }

  @Test
  fun `different requestors retain independent timer requests`() {
    val library = RecordingWinMM()
    val helper = JBAnimatorHelper(library)
    val first = JBAnimator(disposable)
    val second = JBAnimator(disposable)
    helper.request(first)
    helper.request(second)
    helper.cancel(first)
    helper.cancel(second)
    assertThat(library.calls).containsExactly("begin:1", "begin:1", "end:1", "end:1")
  }

  @Test
  fun `reset retains the existing single release and clears requestors`() {
    val library = RecordingWinMM()
    val helper = JBAnimatorHelper(library)
    val first = JBAnimator(disposable)
    val second = JBAnimator(disposable)
    helper.request(first)
    helper.request(second)
    helper.reset()
    helper.reset()
    helper.cancel(first)
    helper.cancel(second)
    helper.request(first)
    helper.cancel(first)
    assertThat(library.calls).containsExactly("begin:1", "begin:1", "end:1", "begin:1", "end:1")
  }

  @Test
  fun `non-Windows systems do not load winmm`() {
    val library = JBAnimatorHelper.loadWinMM(false, { error("The native loader must not run") }, { throw AssertionError(it) })
    assertThat(library.timeBeginPeriod(1)).isZero()
    assertThat(library.timeEndPeriod(1)).isZero()
  }

  @Test
  fun `a loaded binding is retained`() {
    val expected = RecordingWinMM()
    val library = JBAnimatorHelper.loadWinMM(true, { expected }, { throw AssertionError(it) })
    assertThat(library).isSameAs(expected)
  }

  @Test
  fun `binding failure retains the cause and returns a no-op binding`() {
    val failure = IllegalStateException("The native symbol is unavailable")
    val failures = mutableListOf<Throwable>()
    val library = JBAnimatorHelper.loadWinMM(true, { throw failure }, { failures.add(it) })
    assertThat(failures).hasSize(1)
    assertThat(failures.single()).hasMessage("Cannot load 'winmm.dll' library").hasCause(failure)
    assertThat(library.timeBeginPeriod(1)).isZero()
    assertThat(library.timeEndPeriod(1)).isZero()
  }

  @Test
  fun `a missing library retains the existing suppressed link error`() {
    val failures = mutableListOf<Throwable>()
    JBAnimatorHelper.loadWinMM(true, { throw UnsatisfiedLinkError("winmm") }, { failures.add(it) })
    assertThat(failures).hasSize(1)
    assertThat(failures.single()).hasMessage("Cannot load 'winmm.dll' library").hasNoCause()
  }

  @Test
  fun `availability stays disabled outside Windows`() {
    assumeFalse(SystemInfoRt.isWindows)
    assertThat(JBAnimatorHelper.isAvailable()).isFalse()
    assertThrows<IllegalArgumentException> { JBAnimatorHelper.setAvailable(true) }
  }

  @Test
  @EnabledOnOs(WINDOWS)
  fun `native timer requests can be acquired and released`() {
    val library = JBAnimatorHelper.createWinMM()
    val result = library.timeBeginPeriod(1)
    try {
      assertThat(result).isZero()
    }
    finally {
      if (result == 0) {
        assertThat(library.timeEndPeriod(1)).isZero()
      }
    }
  }

  private class RecordingWinMM : JBAnimatorHelper.WinMM {
    val calls = mutableListOf<String>()

    override fun timeBeginPeriod(period: Int): Int {
      calls.add("begin:$period")
      return 0
    }

    override fun timeEndPeriod(period: Int): Int {
      calls.add("end:$period")
      return 0
    }
  }
}
