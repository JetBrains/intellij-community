import kotlinx.serialization.internal.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Deprecated(
    message = "This synthesized declaration should not be used directly",
    level = DeprecationLevel.HIDDEN
)
object `$serializer` : GeneratedSerializer<A> {
    override fun serialize(encoder: Encoder, value: A)
    override fun deserialize(decoder: Decoder): A
    val descriptor: SerialDescriptor
    override fun childSerializers(): Array<KSerializer<*>>
}

class A