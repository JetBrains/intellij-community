import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer


@Serializer(MyClass::class)
class CustomSerializer {

/*<# block [val   descriptor :   SerialDescriptor ]
[ override   fun   serialize ( encoder :   Encoder ,   value :   MyClass ) ]
[ override   fun   deserialize ( decoder :   Decoder ) :   MyClass] #>*/
}
