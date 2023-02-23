import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.APP)
class MyService(val project: CoroutineScope)