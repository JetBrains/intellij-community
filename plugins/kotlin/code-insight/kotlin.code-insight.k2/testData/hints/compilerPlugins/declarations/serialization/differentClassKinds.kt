import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer

class MyClass

@Serializer(MyClass::class)
enum class Enum {
    X {

    },
    Y
/*<# block [val   descriptor :   SerialDescriptor ]
[ override   fun   serialize ( encoder :   Encoder ,   value :   MyClass ) ]
[ override   fun   deserialize ( decoder :   Decoder ) :   MyClass] #>*/
}

@Serializer(MyClass::class)
annotation class Annotation/*<# { #>*/
/*<# block [val   descriptor :   SerialDescriptor ]
[ override   fun   serialize ( encoder :   Encoder ,   value :   MyClass ) ]
[ override   fun   deserialize ( decoder :   Decoder ) :   MyClass]
} #>*/

@Serializer(MyClass::class)
object Object/*<# { #>*/
/*<# block [val   descriptor :   SerialDescriptor ]
[ override   fun   serialize ( encoder :   Encoder ,   value :   MyClass ) ]
[ override   fun   deserialize ( decoder :   Decoder ) :   MyClass]
} #>*/

@Serializer(MyClass::class)
class Class/*<# { #>*/
/*<# block [val   descriptor :   SerialDescriptor ]
[ override   fun   serialize ( encoder :   Encoder ,   value :   MyClass ) ]
[ override   fun   deserialize ( decoder :   Decoder ) :   MyClass]
} #>*/

@Serializer(MyClass::class)
interface Inteface/*<# { #>*/
/*<# block [val   descriptor :   SerialDescriptor ]
[ override   fun   serialize ( encoder :   Encoder ,   value :   MyClass ) ]
[ override   fun   deserialize ( decoder :   Decoder ) :   MyClass]
} #>*/

@Serializer(MyClass::class)
abstract class XXX {/*<# ... #>*/}

@Serializer(MyClass::class)
class Outer {

    @Serializer(MyClass::class)
    companion object {

/*<# block [val   descriptor :   SerialDescriptor ]
[ override   fun   serialize ( encoder :   Encoder ,   value :   MyClass ) ]
[ override   fun   deserialize ( decoder :   Decoder ) :   MyClass] #>*/
    }

    @Serializer(MyClass::class)
    class Inner {
/*<# block [val   descriptor :   SerialDescriptor ]
[ override   fun   serialize ( encoder :   Encoder ,   value :   MyClass ) ]
[ override   fun   deserialize ( decoder :   Decoder ) :   MyClass] #>*/
    }
/*<# block [val   descriptor :   SerialDescriptor ]
[ override   fun   serialize ( encoder :   Encoder ,   value :   MyClass ) ]
[ override   fun   deserialize ( decoder :   Decoder ) :   MyClass] #>*/
}
