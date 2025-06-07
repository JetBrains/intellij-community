package test

import test.Holder.OriginalClass as AliasedClass

class Holder {
    class OriginalClass
} 

fun foo(): OriginalCl<caret>

// IGNORE_K1
// ELEMENT: "OriginalClass"