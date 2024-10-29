// FIR_IDENTICAL
package foo

class Impl: B {
    <caret>
}

// MEMBER_K2: "foo(r: Runnable?): Unit"
// MEMBER_K1: "foo(r: Runnable!): Unit"