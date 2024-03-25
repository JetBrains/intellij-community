// MODE: all
import java.util.Collections

typealias A = kotlin.Int
fun m(l: java.util.List<A>) {
    val x/*<# : |[A:kotlin.fqn.class]A|! #>*/ = l[0]
}
val singleton/*<# : |(|[kotlin.collections.MutableSet:kotlin.fqn.class]Mutable|)|[kotlin.collections.Set:kotlin.fqn.class]Set|<|[kotlin.String:kotlin.fqn.class]String|!|> #>*/ = Collections.singleton("scotch")