import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service

@Suppress("NO_REFLECTION_IN_CLASS_PATH")
@Service(Service.Level.APP)
class MyService {
  companion object {
    @JvmStatic
    val instance: MyService
      get() = ApplicationManager.getApplication().getService(MyService::class.java)
  }
}