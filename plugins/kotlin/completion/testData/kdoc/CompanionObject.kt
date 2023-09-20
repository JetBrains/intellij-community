// FIR_COMPARISON
// FIR_IDENTICAL
/**
 * [A.<caret>]
 */
class A {
    companion object {
        fun staticMember() {}
    }
}

// EXIST: {"lookupString":"Companion","tailText":" (A)","icon":"org/jetbrains/kotlin/idea/icons/objectKotlin.svg","attributes":""}
// EXIST: {"lookupString":"staticMember","tailText":"()","typeText":"Unit","icon":"Method","attributes":"bold"}
// IGNORE_K2
