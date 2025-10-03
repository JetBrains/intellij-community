import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.ThreadingAssertions

class SubtypingPolymorphism {
  fun testMethod() {
    val services: List<BaseService> = listOf(FileService(), UIService(), DBService())
    services.forEach { it.execute() }
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
