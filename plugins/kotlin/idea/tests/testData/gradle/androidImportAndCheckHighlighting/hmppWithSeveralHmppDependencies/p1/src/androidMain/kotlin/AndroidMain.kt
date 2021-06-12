@file:Suppress("unused")

import android.content.Context
import io.ktor.client.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.locks.ReentrantLock

class AndroidMain : JvmAndAndroidMain {
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

    override fun <lineMarker descr="Overrides function in 'JvmAndAndroidMain'">useJdkApis</lineMarker>(): ReentrantLock {
        return ReentrantLock()
    }

    fun useAndroidApis(context: Context): String {
        return context.applicationContext.packageName
    }
}
