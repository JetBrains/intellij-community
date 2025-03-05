// MODE: inheritors

interface SomeInterface {/*<# [2 Implementations] #>*/
fun interfaceMethodA()/*<# [2 Implementations] #>*/
}
open class SomeClass : SomeInterface {/*<# [1 Inheritor] #>*/
override fun interfaceMethodA() {} // <== (1)/*<# [1 Override] #>*/
}

class SomeDerivedClass : SomeClass() {
    override fun interfaceMethodA() {} // <== (2)
}