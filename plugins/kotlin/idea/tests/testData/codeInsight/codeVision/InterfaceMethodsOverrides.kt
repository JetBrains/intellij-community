// MODE: inheritors

interface SomeInterface {/*<# [1 Implementation] #>*/
fun interfaceMethodA() = 10/*<# [1 Override] #>*/
}

class SomeClass : SomeInterface {
    override fun interfaceMethodA() = 20 // <== (1)
}