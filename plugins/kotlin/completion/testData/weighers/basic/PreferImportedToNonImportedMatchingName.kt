package test

abstract class MatchingClass

class LocalModifier

fun test(otherModifier: MatchingClass) {

}

fun foo() {
    test(<caret>)
}

// We want the local modifier to be preferred even though it is a worse match
// ORDER: otherModifier =
// ORDER: MatchingClass
// ORDER: object
// ORDER: LocalModifier
// IGNORE_K1
