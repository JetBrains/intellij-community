// MODE: all
sealed class Aaaaaaaaaaaaaa {
    class Bbbbbbbbbbbbbbbccc: Aaaaaaaaaaaaaa()
}
fun main(args: Array<String>) {
    val list/*<# : |[kotlin.collections.List:kotlin.fqn.class]List|<|[Aaaaaaaaaaaaaa.Bbbbbbbbbbbbbbbccc:kotlin.fqn.class]Aaaaaaaaaaaaaa.Bbbbbbbbbbbbbbb…|> #>*/ = listOf(Aaaaaaaaaaaaaa.Bbbbbbbbbbbbbbbccc())
}