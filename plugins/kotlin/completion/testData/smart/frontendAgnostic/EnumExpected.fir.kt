// FIR_COMPARISON
// IGNORE_K2
// REGISTRY: kotlin.k2.smart.completion.enabled true
package sample

enum class Foo {
  X, Y
}

fun foo() {
  val f: Foo = <caret>
}

// WITH_ORDER
// EXIST: { lookupString:"X", itemText:"Foo.X", typeText:"Foo" }
// EXIST: { lookupString:"Y", itemText:"Foo.Y", typeText:"Foo" }
// NOTHING_ELSE
