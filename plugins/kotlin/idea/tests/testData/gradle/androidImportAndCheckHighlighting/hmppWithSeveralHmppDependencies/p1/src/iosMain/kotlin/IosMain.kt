@file:Suppress("unused")

import io.ktor.client.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.update
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class IosMain: CommonMain {
    override fun <lineMarker descr="Overrides function in CommonMain Press Ctrl+U to navigate">useKtorApis</lineMarker>(): HttpClient {
        return super.useKtorApis().config {
            this.useDefaultTransformers = true
        }
    }

    override fun <lineMarker descr="Overrides function in CommonMain Press Ctrl+U to navigate">useCoroutinesApis</lineMarker>(): Deferred<String> {
        return runBlocking(Dispatchers.Main) {
            super.useCoroutinesApis()
        }
    }

    override fun <lineMarker descr="Overrides function in CommonMain Press Ctrl+U to navigate">useKtorApisCloseable</lineMarker>(): Closeable {
        return object: Closeable {
            override fun <lineMarker descr="Implements function in Closeable (io.ktor.utils.io.core) Press Ctrl+U to navigate">close</lineMarker>() = Unit
        }
    }

    override fun <lineMarker descr="Overrides function in CommonMain Press Ctrl+U to navigate">useAtomicFu</lineMarker>(): AtomicInt {
        return super.useAtomicFu().also { atomicInt ->
            atomicInt.update { it + 1 }
        }
    }

    override fun <lineMarker descr="Overrides function in CommonMain Press Ctrl+U to navigate">androidSdkIsNotVisible</lineMarker>(context: <error descr="[UNRESOLVED_REFERENCE] Unresolved reference: android" textAttributesKey="null">android</error>.<error descr="[DEBUG] Reference is not resolved to anything, but is not marked unresolved" textAttributesKey="KOTLIN_DEBUG_INFO">content</error>.<error descr="[DEBUG] Reference is not resolved to anything, but is not marked unresolved" textAttributesKey="KOTLIN_DEBUG_INFO">Context</error>) {

    }
}
