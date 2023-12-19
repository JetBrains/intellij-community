// MODE: all
typealias A = kotlin.Int
fun m(l: java.util.List<A>) {
    val x/*<# : |A! #>*/ = l[0]
}