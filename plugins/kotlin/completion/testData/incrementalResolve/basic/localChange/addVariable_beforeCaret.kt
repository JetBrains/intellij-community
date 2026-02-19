package test

fun test() {
    val p0 = 5
    <change>
    val p2 = <before><caret>
}

// TYPE: "val p1 = 10"
// EXIST: p0
// EXIST: p1