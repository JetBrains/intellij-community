package bar

fun buz() {
    Bar.<caret>
}

// IGNORE_K2
// EXIST: bconst
// EXIST: bval
// EXIST: Companion
// EXIST: FooBar
// EXIST: bfun
// EXIST: toString
// EXIST: hashCode
// EXIST: equals
// NOTHING_ELSE
