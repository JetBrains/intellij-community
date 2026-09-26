import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer

class MyClass

interface Interface

open class Super


@Serializer(MyClass::class)
class ManyNewLines :


    Interface,


    Super()/*<# , |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/

@Serializer(MyClass::class)
class ManyNewLinesAndComments :
/*fdsfs


dd*/

    Interface,


    Super()/*<# , |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*//*fdsfs

dd*/


@Serializer(MyClass::class)
class MultilineCommentAtTheEnd/*<#  : |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/ /**/

@Serializer(MyClass::class)
class SinglelineCommentAtTheEnd/*<#  : |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*/// a

@Serializer(MyClass::class)
class CommentAfterPrimaryConstructor()/*<#  : |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*//**/


@Serializer(MyClass::class)
class CommentAfterName/*<#  : |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*//**/

@Serializer(MyClass::class)
class CommentAfterNameWithBody/*<#  : |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*//**/ {
}


@Serializer(MyClass::class)
class CommentAfterPrimaryConstructorWithBody()/*<#  : |[kotlinx.serialization.KSerializer:kotlin.fqn.class]KSerializer|<|[MyClass:kotlin.fqn.class]MyClass|> #>*//**/ {
}