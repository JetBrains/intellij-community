import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

@Suppress("UNUSED_PARAMETER")
@Service(Service.Level.APP)
class MyService(scope: CoroutineScope)