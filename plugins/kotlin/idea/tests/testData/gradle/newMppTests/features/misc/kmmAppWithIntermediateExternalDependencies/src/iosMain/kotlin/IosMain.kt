//region Test configuration
// - hidden: line markers
//endregion
@file:Suppress("UNUSED")

import io.ktor.client.*
import io.ktor.utils.io.core.*
import okio.Buffer

class IosMain: CommonMain {
    override fun useKtorApis(): HttpClient {
        return super.useKtorApis().config {
            this.useDefaultTransformers = true
        }
    }

    override fun useKtorApisCloseable(): Closeable {
        return object: Closeable {
            override fun close() = Unit
        }
    }

    fun okio(buffer: Buffer) {
        buffer.flush()
    }
}
