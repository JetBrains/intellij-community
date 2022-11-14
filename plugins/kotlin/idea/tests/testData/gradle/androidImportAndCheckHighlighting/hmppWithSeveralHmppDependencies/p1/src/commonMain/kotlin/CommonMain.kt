import io.ktor.client.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*

interface <lineMarker descr="Is implemented by     AndroidMain     IosMain     JvmAndAndroidMain     JvmMain  Click or press ⌥⌘Bto navigate">CommonMain</lineMarker> {
    fun <lineMarker descr="Is overridden in     AndroidMain     IosMain     JvmAndAndroidMain     JvmMain">useKtorApis</lineMarker>(): HttpClient {
        return HttpClient {
            this.developmentMode = true
            this.expectSuccess  = true
        }.config {
            this.useDefaultTransformers = true
        }
    }

    fun <lineMarker descr="Is overridden in     AndroidMain     IosMain     JvmAndAndroidMain     JvmMain">useKtorApisCloseable</lineMarker>(): Closeable {
        return object: Closeable {
            override fun <lineMarker descr="Implements function in 'Closeable'">close</lineMarker>() = Unit
        }
    }

    fun <lineMarker descr="Is overridden in     AndroidMain     IosMain     JvmAndAndroidMain     JvmMain">useCoroutinesApis</lineMarker>(): Deferred<String> {
        return MainScope().async(Dispatchers.Main) {
            <lineMarker descr="Suspend function call">withContext</lineMarker>(Dispatchers.Default) {
                "This code is abusing coroutines! 🤷"
            }
        }
    }

    fun <lineMarker descr="Is overridden in     IosMain     JvmAndAndroidMain     JvmMain">useAtomicFu</lineMarker>(): AtomicInt {
        return atomic(0).also {
            it.update { value -> value + 1 }
        }
    }

    fun <lineMarker descr="Is overridden in     IosMain     JvmAndAndroidMain     JvmMain">androidSdkIsNotVisible</lineMarker>(context: android.<error descr="[UNRESOLVED_REFERENCE] Unresolved reference: content" textAttributesKey="null">content</error>.<error descr="[DEBUG] Reference is not resolved to anything, but is not marked unresolved" textAttributesKey="KOTLIN_DEBUG_INFO">Context</error>) {

    }
}
