import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer

class MyClass

@Serializer(MyClass::class)
enum class Enum/*<#  : |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/ {
    X {

    },
    Y
}

@Serializer(MyClass::class)
annotation class Annotation/*<#  : |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/

@Serializer(MyClass::class)
object Object/*<#  : |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/

@Serializer(MyClass::class)
class Class/*<#  : |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/

@Serializer(MyClass::class)
interface Inteface/*<#  : |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/

@Serializer(MyClass::class)
abstract class XXX/*<#  : |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/ {}

@Serializer(MyClass::class)
class Outer/*<#  : |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/ {

    @Serializer(MyClass::class)
    companion object/*<#  : |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/ {

    }
}