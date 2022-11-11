// FIR_COMPARISON
package bar

import javapackage.SomeInterface

fun buz {
    SomeInterface.<caret>
}

// ABSENT: aProc
// EXIST: CONST_A
// EXIST: aStaticProc
// EXIST: getA
// EXIST: FooBar
// NOTHING_ELSE