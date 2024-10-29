// MODE: all
interface MyInterface

private val privateAnonymousObjects/*<# : |[kotlin.collections.List:kotlin.fqn.class]List|<|<anonymous>|> #>*/ = listOf(object : MyInterface{})

val publicAnonymousObjects/*<# : |[kotlin.collections.List:kotlin.fqn.class]List|<|[MyInterface:kotlin.fqn.class]MyInterface|> #>*/ = listOf(object : MyInterface{})