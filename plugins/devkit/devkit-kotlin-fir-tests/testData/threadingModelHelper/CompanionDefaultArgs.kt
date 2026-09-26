import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.ThreadingAssertions

class CompanionDefaultArgs {
  fun testMethod() {
    annoFun()
    defaultArgFun()
  }

  companion object {
    @RequiresReadLock
    @JvmStatic
    fun annoFun() {}
  }

  fun defaultArgFun(value: Int = 0): Int {
    ThreadingAssertions.assertReadAccess()
    return value
  }
}
