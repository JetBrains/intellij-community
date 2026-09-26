// FIR_COMPARISON
// REGISTRY: kotlin.k2.smart.completion.enabled true
package sample

enum class Foo {
  X, Y;

  fun foo(): Foo = this
}

fun foo() {
  val f: Foo = <caret>
}

// EXIST: { lookupString:"X", itemText:"Foo.X", typeText:"Foo" }
// EXIST: { lookupString:"Y", itemText:"Foo.Y", typeText:"Foo" }
// ABSENT: { itemText:"Foo.foo" }
