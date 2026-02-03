// WITH_STDLIB
fun literal() {
    var b : Boolean
    b = true
    println(<weak_warning descr="Value of 'b' is always true">b</weak_warning>)
}
fun returnLiteral() : Boolean = false
fun booleanBoxed(x:Boolean?) {
    if (x != true) { }
}
fun noWarningOnConstant(x : Boolean) {
    val b = true
    if (x || b) {}
}
fun polyadic(x: Boolean, y: Boolean, z: Boolean) {
    if (x) {
        if (y && <warning descr="Condition 'x' is always true when reached">x</warning>) {}
        if (<warning descr="Condition 'y && !x' is always false">y && <warning descr="Condition '!x' is always false when reached">!x</warning></warning>) {}
        if (<warning descr="Condition 'y || x' is always true">y || <warning descr="Condition 'x' is always true when reached">x</warning></warning>) {}
        if (y || <warning descr="Condition '!x' is always false when reached">!x</warning>) {}
        if (y && <warning descr="Condition 'x' is always true when reached">x</warning> && z) {}
        if (<warning descr="Condition 'y && !x && z' is always false"><warning descr="Condition 'y && !x' is always false">y && <warning descr="Condition '!x' is always false when reached">!x</warning></warning> && z</warning>) {}
        if (<warning descr="Condition 'y || x || z' is always true"><warning descr="Condition 'y || x' is always true">y || <warning descr="Condition 'x' is always true when reached">x</warning></warning> || z</warning>) {}
        if (y || <warning descr="Condition '!x' is always false when reached">!x</warning> || z) {}
        if (<warning descr="Condition 'x' is always true">x</warning> && y) {}
        if (<warning descr="Condition '!x && y' is always false"><warning descr="Condition '!x' is always false">!x</warning> && y</warning>) {}
        if (<warning descr="Condition 'x || y' is always true"><warning descr="Condition 'x' is always true">x</warning> || y</warning>) {}
        if (<warning descr="Condition '!x' is always false">!x</warning> || y) {}
    }
}
fun falseOrNullElvis(b: Boolean?):Boolean {
    // TODO: always false; we cannot track (null|false) as a single state. Probably split states at ==?
    if (b == true) return true
    return b ?: false
}
