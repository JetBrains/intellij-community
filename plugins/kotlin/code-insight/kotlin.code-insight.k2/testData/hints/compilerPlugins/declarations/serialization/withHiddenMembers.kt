// WITH_HIDDEN_MEMBERS

import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer


@Serializable
class MyClass {

/*<# block [companion   object   { ]
[     fun   serializer ( ) :   KSerializer < MyClass > ]
[ } ]

[ @ Deprecated ( ]
[     message   =   " This synthesized declaration should not be used directly " , ]
[     level   =   kotlin . DeprecationLevel . HIDDEN ]
[ ) ]
[ object   `$serializer`   :   GeneratedSerializer < MyClass >   { ]
[     override   fun   serialize ( encoder :   Encoder ,   value :   MyClass ) ]
[     override   fun   deserialize ( decoder :   Decoder ) :   MyClass ]
[     val   descriptor :   SerialDescriptor ]
[     override   fun   childSerializers ( ) :   Array < KSerializer < * > > ]
[ }] #>*/
}


@Serializer(MyClass::class)
class CustomSerialize {

/*<# block [val   descriptor :   SerialDescriptor ]
[ override   fun   serialize ( encoder :   Encoder ,   value :   MyClass ) ]
[ override   fun   deserialize ( decoder :   Decoder ) :   MyClass] #>*/
}
