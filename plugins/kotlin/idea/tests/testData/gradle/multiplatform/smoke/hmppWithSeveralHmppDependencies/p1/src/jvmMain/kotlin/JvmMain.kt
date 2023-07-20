//region Test configuration
// - hidden: line markers
//endregion
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
    override fun useKtorApis(): HttpClient {
        return super.useKtorApis().config {
            this.useDefaultTransformers = true
        }
    }

    override fun useKtorApisCloseable(): Closeable {
        return Closeable { }
    }

    override fun useCoroutinesApis(): Deferred<String> {
        return runBlocking(Dispatchers.Main) {
            super.useCoroutinesApis()
        }
    }

    override fun useAtomicFu(): AtomicInt {
        return super.useAtomicFu()
            .also { atomicInt -> atomicInt.update { value -> value + 1 } }
    }

    override fun androidSdkIsNotVisible(context: <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: Context'")!>Context<!>) {

    }

    override fun useJdkApis(): ReentrantLock {
        return ReentrantLock()
    }
}
