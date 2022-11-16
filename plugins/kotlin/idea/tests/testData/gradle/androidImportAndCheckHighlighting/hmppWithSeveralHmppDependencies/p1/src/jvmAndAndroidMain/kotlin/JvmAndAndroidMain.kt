@file:Suppress("unused")

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.utils.io.core.*
import io.reactivex.rxjava3.core.Observable
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.update
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.locks.ReentrantLock

interface <lineMarker descr="Is implemented by     AndroidMain     JvmMain  Click or press ⌥⌘Bto navigate">JvmAndAndroidMain</lineMarker> : CommonMain {
    override fun <lineMarker descr="Overrides function in 'CommonMain'"><lineMarker descr="Is overridden in     AndroidMain     JvmMain">useKtorApis</lineMarker></lineMarker>(): HttpClient {
        return super.useKtorApis().config {
            this.useDefaultTransformers = true
        }
    }

    override fun <lineMarker descr="Overrides function in 'CommonMain'"><lineMarker descr="Is overridden in     AndroidMain     JvmMain">useKtorApisCloseable</lineMarker></lineMarker>(): Closeable {
        return Closeable {  }
    }

    override fun <lineMarker descr="Overrides function in 'CommonMain'"><lineMarker descr="Is overridden in     AndroidMain     JvmMain">useCoroutinesApis</lineMarker></lineMarker>(): Deferred<String> {
        return runBlocking(Dispatchers.IO) {
            super.useCoroutinesApis()
        }
    }

    override fun <lineMarker descr="Overrides function in 'CommonMain'"><lineMarker descr="Is overridden in     JvmMain">useAtomicFu</lineMarker></lineMarker>(): AtomicInt {
        return super.useAtomicFu().also { atomicInt ->
            atomicInt.update { it + 1 }
        }
    }


    override fun <lineMarker descr="Overrides function in 'CommonMain'"><lineMarker descr="Is overridden in     JvmMain">androidSdkIsNotVisible</lineMarker></lineMarker>(context: <error descr="[UNRESOLVED_REFERENCE] Unresolved reference: android" textAttributesKey="null">android</error>.<error descr="[DEBUG] Reference is not resolved to anything, but is not marked unresolved" textAttributesKey="KOTLIN_DEBUG_INFO">content</error>.<error descr="[DEBUG] Reference is not resolved to anything, but is not marked unresolved" textAttributesKey="KOTLIN_DEBUG_INFO">Context</error>) {

    }

    fun <lineMarker descr="Is overridden in     AndroidMain     JvmMain">useJdkApis</lineMarker>(): ReentrantLock {
        return ReentrantLock()
    }

    fun useRxJava(): Observable<String> {
        return Observable.fromIterable(listOf("Hello", "RX"))
    }

    fun useJackson(): ObjectMapper {
        return jacksonObjectMapper()
            .configure(JsonParser.Feature.IGNORE_UNDEFINED, true)
    }
}
