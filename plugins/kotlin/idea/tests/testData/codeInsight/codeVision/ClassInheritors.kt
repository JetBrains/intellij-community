// MODE: inheritors

open class SomeClass {/*<# [5 Inheritors] #>*/
class NestedDerivedClass: SomeClass() {} // <== (1): nested class
}
open class DerivedClass : SomeClass {} // <== (2): direct derived one/*<# [1 Inheritor] #>*/
class AnotherDerivedClass : SomeClass {} // <== (3): yet another derived one
class DerivedDerivedClass : DerivedClass { // <== (): indirect inheritor of SomeClass
    fun main() {
        val someClassInstance = object : SomeClass() { // <== (4): anonymous derived one
            val somethingHere = ""
        }
    }
}