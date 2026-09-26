// MODE: usages

interface SomeClass {/*<# [1 Usage] #>*/
var someProperty = "initialized"/*<# [3 Usages] #>*/
    fun someFun() = "it's " + someProperty // <== (1): reference from expression
}

fun main() {
    val instance = object: SomeClass {}
    val someString = instance.someProperty // <== (2): getter call
    instance.someProperty = "anotherValue" // <== (3): setter call