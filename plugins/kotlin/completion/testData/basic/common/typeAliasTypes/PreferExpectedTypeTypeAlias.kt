// WITH_STDLIB
package test

class SomePrefixFooOriginal
class SomePrefixFooAa
typealias SomePrefixFooAlias = SomePrefixFooOriginal
class SomePrefixFooBb

fun foo(foo: SomePrefixFooOriginal) {}

fun test() {
    foo(SomePrefix<caret>)
}

// WITH_ORDER
// EXIST: SomePrefixFooAlias
// EXIST: SomePrefixFooOriginal
// EXIST: SomePrefixFooAa
// EXIST: SomePrefixFooBb
