package bar

fun buz() {
    Color.<caret>
}

// IGNORE_K2
// EXIST: RED
// EXIST: GREEN
// EXIST: BLUE
// EXIST: values
// EXIST: valueOf
// NOTHING_ELSE
