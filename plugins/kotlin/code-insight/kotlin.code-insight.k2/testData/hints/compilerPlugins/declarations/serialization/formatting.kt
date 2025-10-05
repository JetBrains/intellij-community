import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer

class MyClass


@Serializer(MyClass::class)
class BracesCSharpStyle
{

/*<# block [val   descriptor :   SerialDescriptor ]
[ override   fun   serialize ( encoder :   Encoder ,   value :   MyClass ) ]
[ override   fun   deserialize ( decoder :   Decoder ) :   MyClass] #>*/
}


class NestedClassesNoFormatting {
@Serializer(MyClass::class)
class Nested {

/*<# block [val   descriptor :   SerialDescriptor ]
[ override   fun   serialize ( encoder :   Encoder ,   value :   MyClass ) ]
[ override   fun   deserialize ( decoder :   Decoder ) :   MyClass] #>*/
}
}
