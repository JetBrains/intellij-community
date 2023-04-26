//region Test configuration
// - hidden: line markers
//endregion
@file:Suppress("unused")

import android.content.Context
import io.ktor.client.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.locks.ReentrantLock

class AndroidMain : JvmAndAndroidMain {
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

    override fun useJdkApis(): ReentrantLock {
        return ReentrantLock()
    }

    fun useAndroidApis(context: Context): String {
        return context.applicationContext.packageName
    }
}
