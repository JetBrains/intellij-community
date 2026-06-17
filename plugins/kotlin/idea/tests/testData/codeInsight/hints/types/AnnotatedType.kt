// MODE: local_variable
@Target(AnnotationTarget.TYPE)
annotation class Relative

class Path(val value: String)

fun test() {
    val list/*<# : |[kotlin.collections.List:kotlin.fqn.class]List|<|@|[Relative:kotlin.fqn.class]Relative| |[Path:kotlin.fqn.class]Path|> #>*/ = listOf<@Relative Path>(Path("foo"), Path("bar"))
    val check: List<@Relative Path> = list
}
