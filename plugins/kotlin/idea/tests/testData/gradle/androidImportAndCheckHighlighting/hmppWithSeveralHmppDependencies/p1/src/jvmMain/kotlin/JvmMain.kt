@file:Suppress("unused")

import io.ktor.client.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.update
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.locks.ReentrantLock

class JvmMain: JvmAndAndroidMain {
    override fun <lineMarker descr="Overrides function in 'JvmAndAndroidMain'">useKtorApis</lineMarker>(): HttpClient {
        return super.useKtorApis().config {
            this.useDefaultTransformers = true
        }
    }

    override fun <lineMarker descr="Overrides function in 'JvmAndAndroidMain'">useKtorApisCloseable</lineMarker>(): Closeable {
        return Closeable { }
    }

    override fun <lineMarker descr="Overrides function in 'JvmAndAndroidMain'">useCoroutinesApis</lineMarker>(): Deferred<String> {
        return runBlocking(Dispatchers.Main) {
            super.useCoroutinesApis()
        }
    }

    override fun <lineMarker descr="Overrides function in 'JvmAndAndroidMain'">useAtomicFu</lineMarker>(): AtomicInt {
        return super.useAtomicFu()
            .also { atomicInt -> atomicInt.update { value -> value + 1 } }
    }

    override fun <lineMarker descr="Overrides function in 'JvmAndAndroidMain'">androidSdkIsNotVisible</lineMarker>(context: <error descr="[UNRESOLVED_REFERENCE] Unresolved reference: android" textAttributesKey="null">android</error>.<error descr="[DEBUG] Reference is not resolved to anything, but is not marked unresolved" textAttributesKey="KOTLIN_DEBUG_INFO">content</error>.<error descr="[DEBUG] Reference is not resolved to anything, but is not marked unresolved" textAttributesKey="KOTLIN_DEBUG_INFO">Context</error>) {

    }

    override fun <lineMarker descr="Overrides function in 'JvmAndAndroidMain'">useJdkApis</lineMarker>(): ReentrantLock {
        return ReentrantLock()
    }
}
