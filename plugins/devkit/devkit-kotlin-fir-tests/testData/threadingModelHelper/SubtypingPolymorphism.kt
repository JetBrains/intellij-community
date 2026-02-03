import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.ThreadingAssertions

class SubtypingPolymorphism {
  fun testMethod(p: BaseService) {
    p.execute()
  }
}

interface BaseService {
  fun execute()
}

class FileService : BaseService {
  @RequiresReadLock
  override fun execute() {
  }
}

class UIService : BaseService {
  override fun execute() {
    ThreadingAssertions.assertEventDispatchThread()
  }
}

class DBService : BaseService {
  override fun execute() {
    ThreadingAssertions.assertReadAccess()
  }
}
