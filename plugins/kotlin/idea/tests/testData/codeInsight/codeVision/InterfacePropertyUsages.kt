// MODE: usages

interface SomeInterface {/*<# [1 Usage] #>*/
val someProperty = "initialized"/*<# [2 Usages] #>*/
    fun someFun() = "it's " + someProperty // <== (1):
}

fun main() {
    val instance = object: SomeInterface {}
    val someString = instance.someProperty // <== (2): call on an instance