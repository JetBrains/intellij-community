import io.ktor.client.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*

interface <lineMarker descr="Is implemented by AndroidMain IosMain JvmAndAndroidMain JvmMain Press Ctrl+Alt+B to navigate">CommonMain</lineMarker> {
    fun <lineMarker descr="Is overridden in AndroidMain IosMain JvmAndAndroidMain JvmMain Press Ctrl+Alt+B to navigate">useKtorApis</lineMarker>(): HttpClient {
        return HttpClient {
            this.developmentMode = true
            this.expectSuccess  = true
        }.config {
            this.useDefaultTransformers = true
        }
    }

    fun <lineMarker descr="Is overridden in AndroidMain IosMain JvmAndAndroidMain JvmMain Press Ctrl+Alt+B to navigate">useKtorApisCloseable</lineMarker>(): Closeable {
        return object: Closeable {
            override fun <lineMarker descr="Implements function in Closeable (io.ktor.utils.io.core) Press Ctrl+U to navigate">close</lineMarker>() = Unit
        }
    }

    fun <lineMarker descr="Is overridden in AndroidMain IosMain JvmAndAndroidMain JvmMain Press Ctrl+Alt+B to navigate">useCoroutinesApis</lineMarker>(): Deferred<String> {
        return MainScope().async(Dispatchers.Main) {
            <lineMarker descr="Suspend function call">withContext</lineMarker>(Dispatchers.Default) {
                "This code is abusing coroutines! 🤷"
            }
        }
    }

    fun <lineMarker descr="Is overridden in IosMain JvmAndAndroidMain JvmMain Press Ctrl+Alt+B to navigate">useAtomicFu</lineMarker>(): AtomicInt {
        return atomic(0).also {
            it.update { value -> value + 1 }
        }
    }

    fun <lineMarker descr="Is overridden in IosMain JvmAndAndroidMain JvmMain Press Ctrl+Alt+B to navigate">androidSdkIsNotVisible</lineMarker>(context: <error descr="[UNRESOLVED_REFERENCE] Unresolved reference: android" textAttributesKey="null">android</error>.<error descr="[DEBUG] Reference is not resolved to anything, but is not marked unresolved" textAttributesKey="KOTLIN_DEBUG_INFO">content</error>.<error descr="[DEBUG] Reference is not resolved to anything, but is not marked unresolved" textAttributesKey="KOTLIN_DEBUG_INFO">Context</error>) {

    }
}
