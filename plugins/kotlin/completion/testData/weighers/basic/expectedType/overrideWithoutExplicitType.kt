class SomePrefixA
class SomePrefixB
class SomePrefixC

open class OpenClass {
    open val test: SomePrefixB = SomePrefixB()
}

class DerivedClass : OpenClass() {
    override val test = SomePrefix<caret>
}

// ORDER: SomePrefixB, SomePrefixA, SomePrefixC
// IGNORE_K1
// IGNORE_K2