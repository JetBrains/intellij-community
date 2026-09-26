import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class KtSpecialConstructs {
  inline fun inlineNoBranch(value: Int): Int = value + 1

  inline fun inlineWithBranch(flag: Boolean): Int {
    return if (flag) 1 else 2
  }

  suspend fun suspendNoBranch(value: Int): Int = value + 1

  suspend fun suspendWithBranch(flag: Boolean): Int {
    suspendHere()
    return if (flag) 10 else 20
  }

  fun defaultArgs(flag: Boolean = false): Int {
    return if (flag) 1 else 2
  }

  open fun openDefaultArgs(flag: Boolean = false): Int {
    return if (flag) 1 else 2
  }

  fun tryFinally(flag: Boolean): Int {
    try {
      return if (flag) 1 else 2
    }
    finally {
      if (flag) {
        println("cleanup")
      }
    }
  }

  lateinit var text: String

  fun lateinitAccess(assign: Boolean): Int {
    if (assign) {
      text = "ready"
    }
    return text.length
  }

  fun unsafeCast(value: Any): String = value as String

  private suspend fun suspendHere(): Unit = suspendCoroutine { continuation ->
    continuation.resume(Unit)
  }
}
