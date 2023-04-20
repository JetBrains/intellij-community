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

class IosMain: CommonMain {
    override fun useKtorApis(): HttpClient {
        return super.useKtorApis().config {
            this.useDefaultTransformers = true
        }
    }

    override fun useCoroutinesApis(): Deferred<String> {
        return runBlocking(Dispatchers.Main) {
            super.useCoroutinesApis()
        }
    }

    override fun useKtorApisCloseable(): Closeable {
        return object: Closeable {
            override fun close() = Unit
        }
    }

    override fun useAtomicFu(): AtomicInt {
        return super.useAtomicFu().also { atomicInt ->
            atomicInt.update { it + 1 }
        }
    }

    override fun androidSdkIsNotVisible(context: <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: Context'")!>Context<!>) {

    }
}
