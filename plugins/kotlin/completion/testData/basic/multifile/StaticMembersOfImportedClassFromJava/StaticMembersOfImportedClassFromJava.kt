// FIR_COMPARISON
package bar

import javapackage.SomeClass

fun buz {
    SomeClass.<caret>
}

// ABSENT: aProc
// EXIST: CONST_A
// EXIST: aStaticProc
// EXIST: getA
// EXIST: FooBar
// NOTHING_ELSE