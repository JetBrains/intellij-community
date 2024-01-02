package test

fun test() {
    val p0 = 0
    val p1<change> = 10
    val p2 = <before><caret>
}

// BACKSPACES: 1
// TYPE: "New"
// EXIST: p0
// ABSENT: p1
// EXIST: pNew
