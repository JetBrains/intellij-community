import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer

class MyClass

@Serializer(MyClass::class)
class WithTypeArgs<T>/*<#  : |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/

interface SuperInterface
open class SuperClass

@Serializer(MyClass::class)
class WithConstructor()/*<#  : |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/

@Serializer(MyClass::class)
class WithConstructorAndTypeArgs<T>()/*<#  : |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/

@Serializer(MyClass::class)
class WithConstructorAndTypeArgsAndSuperClass<T>() : SuperClass()/*<# , |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/

@Serializer(MyClass::class)
class WithConstructorAndTypeArgsAndSuperInterface<T>() : SuperInterface/*<# , |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/

@Serializer(MyClass::class)
class WithConstructorAndTypeArgsAndSuperClassAndInterface<T>() : SuperInterface, SuperClass()/*<# , |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/