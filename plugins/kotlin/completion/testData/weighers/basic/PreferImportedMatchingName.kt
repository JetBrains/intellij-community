package test
import test.other.OtherModifier

abstract class MatchingClass

class LocalModifier

fun test(otherModifier: MatchingClass) {

}

fun foo() {
    test(<caret>)
}

// ORDER: otherModifier =
// ORDER: MatchingClass
// ORDER: OtherModifier
// ORDER: LocalModifier
// IGNORE_K1