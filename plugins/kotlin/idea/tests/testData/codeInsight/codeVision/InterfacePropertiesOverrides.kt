// MODE: inheritors

interface SomeInterface {/*<# [1 Implementation] #>*/
open val interfaceProperty: String/*<# [1 Implementation] #>*/
}

class SomeClass : SomeInterface {
    override val interfaceProperty: String = "overridden" // <== (1)
}