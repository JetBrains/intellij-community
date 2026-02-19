import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer

class MyClass

interface Interface

open class Super

@Serializer(MyClass::class)
class WithInteface : Interface/*<# , |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/

@Serializer(MyClass::class)
class WithClass : Super()/*<# , |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/

@Serializer(MyClass::class)
class WithInterfaceAndClass : Super(), Interface/*<# , |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/

@Serializer(MyClass::class)
class WithInterfaceAndClassEnd : Interface, Super()/*<# , |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/