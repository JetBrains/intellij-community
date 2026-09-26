//region Test configuration
// - hidden: line markers
//endregion
import io.ktor.client.*
import io.ktor.utils.io.core.*

interface CommonMain {
    fun useKtorApis(): HttpClient {
        return HttpClient {
            this.developmentMode = true
            this.expectSuccess  = true
        }.config {
            this.useDefaultTransformers = true
        }
    }

    fun useKtorApisCloseable(): Closeable {
        return object: Closeable {
            override fun close() = Unit
        }
    }
}
