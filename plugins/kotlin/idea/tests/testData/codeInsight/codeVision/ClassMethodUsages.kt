// MODE: usages

abstract class SomeClass {/*<# [1 Usage] #>*/
    abstract fun someFun(): String/*<# [3 Usages] #>*/
    fun someOtherFun() = someFun() // <== (1): delegation from another method
    val someProperty = someFun() // <== (2): property initializer
}

fun main() {
    val instance = object: SomeClass {
        override fun someFun(): String {} // <== (): used below/*<# [1 Usage] #>*/
    }
    instance.someFun() <== (3): call on an instance
}