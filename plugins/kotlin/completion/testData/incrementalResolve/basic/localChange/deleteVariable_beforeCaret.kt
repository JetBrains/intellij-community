package test

fun test() {
    val p0 = 5<change>
    val p1 = 10
    val p2 = <before><caret>
}

// BACKSPACES: 10
// TYPE: ""
// ABSENT: p0
// EXIST: p1
