// MODE: usages

interface SomeInterface {/*<# [1 Usage] #>*/
fun someFun(): String/*<# [3 Usages] #>*/
    fun someOtherFun() = someFun() // <== (1): delegation from another interface method
    val someProperty = someFun() // <== (2): property initializer
}

fun main() {
    val instance = object: SomeInterface {
        override fun someFun(): String {} // <== (): used below/*<# [1 Usage] #>*/
    }
    instance.someFun() <== (3): call on an instance
}