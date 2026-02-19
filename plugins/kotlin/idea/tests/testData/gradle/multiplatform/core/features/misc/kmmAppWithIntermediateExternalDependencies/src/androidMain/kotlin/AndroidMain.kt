//region Test configuration
// - hidden: line markers
//endregion
@file:Suppress("unused")

import io.ktor.client.*
import io.ktor.utils.io.core.*
import java.util.concurrent.locks.ReentrantLock
import io.reactivex.rxjava3.core.*

class AndroidMain: CommonMain {
    override fun useKtorApis(): HttpClient {
        return HttpClient {
            this.developmentMode = true
            this.expectSuccess  = true
        }.config {
            this.useDefaultTransformers = true
        }
    }

    override fun useKtorApisCloseable(): Closeable {
        return object: Closeable {
            override fun close() = Unit
        }
    }

    fun useRxJava() {
        Flowable.just("Hello world").subscribe { println(it) }
    }
}
