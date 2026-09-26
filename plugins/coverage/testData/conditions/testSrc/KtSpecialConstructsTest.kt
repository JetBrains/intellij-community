import junit.framework.TestCase
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class KtSpecialConstructsTest : TestCase() {
  fun testKotlinSpecificConstructs(): Unit = KtSpecialConstructs().run {
    inlineNoBranch(1)
    inlineWithBranch(true)

    runSuspend { suspendNoBranch(1) }
    runSuspend { suspendWithBranch(false) }

    defaultArgs()
    defaultArgs(true)

    openDefaultArgs()
    openDefaultArgs(true)

    tryFinally(true)

    lateinitAccess(true)
    unsafeCast("ready")
  }

  private fun <T> runSuspend(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
      override val context = EmptyCoroutineContext

      override fun resumeWith(resumeResult: Result<T>) {
        result = resumeResult
      }
    })
    return result!!.getOrThrow()
  }
}
