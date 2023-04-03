//region Test configuration
// - hidden: line markers
//endregion
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

interface JvmAndAndroidMain : CommonMain {
    override fun useKtorApis(): HttpClient {
        return super.useKtorApis().config {
            this.useDefaultTransformers = true
        }
    }

    override fun useKtorApisCloseable(): Closeable {
        return Closeable {  }
    }

    override fun useCoroutinesApis(): Deferred<String> {
        return runBlocking(Dispatchers.IO) {
            super.useCoroutinesApis()
        }
    }

    override fun useAtomicFu(): AtomicInt {
        return super.useAtomicFu().also { atomicInt ->
            atomicInt.update { it + 1 }
        }
    }


    override fun androidSdkIsNotVisible(context: <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: Context'")!>Context<!>) {

    }

    fun useJdkApis(): ReentrantLock {
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
