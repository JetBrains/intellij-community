package bar

fun buz {
    SomeClass.<caret>
}

// IGNORE_K2
// EXIST: CONST_A
// EXIST: aProc
// EXIST: getA
// EXIST: FooBar
// NOTHING_ELSE