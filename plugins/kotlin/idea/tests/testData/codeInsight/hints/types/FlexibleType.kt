// MODE: all
import java.util.Collections

typealias A = kotlin.Int
fun m(l: java.util.List<A>) {
    val x/*<# : |A! #>*/ = l[0]
}
val singleton/*<# : |(Mutable)Set<String!> #>*/ = Collections.singleton("scotch")