// MODE: usages

fun function(param: String): Int = 1/*<# [3 Usages] #>*/
fun higherOrderFun(s: String, param: (String) -> Int) = param(s)/*<# [1 Usage] #>*/

fun main() {
    function("someString")
    val functionRef = ::function
    higherOrderFun("someString", ::function)
}