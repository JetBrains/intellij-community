import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock

object TopLevelAndExtension {
  fun testMethod() {
    topLevel()
    extFun()
  }

  fun topLevel() {
    ThreadingAssertions.assertReadAccess()
  }

  @RequiresReadLock
  fun extFun() {}
}
