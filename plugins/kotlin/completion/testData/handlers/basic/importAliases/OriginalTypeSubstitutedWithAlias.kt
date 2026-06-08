package test

import test.Holder.OriginalClass as AliasedClass

class Holder {
    class OriginalClass
} 

fun foo(): OriginalCl<caret>


// ELEMENT: "OriginalClass"