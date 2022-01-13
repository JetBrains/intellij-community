// WITH_RUNTIME
fun inlineLet() {
    val x = 5
    val y = x.let { if(<warning descr="Condition is always true">it == 5</warning>) 1 else 2 }
    if (<warning descr="Condition is always true">y == 1</warning>) {}
    val z = x.let { true }
    val z1 = x.let { false }
    println(<warning descr="Condition is always false"><weak_warning descr="Value is always true">z</weak_warning> && <weak_warning descr="Value is always false">z1</weak_warning></warning>)
}

fun inlineLetQuestion(x: Int?) {
    val res = x?.let { xx -> if (xx > 0) 1 else -1 } ?: 0
    if (res == 0 && <warning descr="Condition is always true when reached">x == null</warning>) {}
    if (res == 1 && x != null && <warning descr="Condition is always true when reached">x > 0</warning>) {}
    if (res == -1 && x != null && <warning descr="Condition is always true when reached">x <= 0</warning>) {}
}

fun inlineAlso() {
    val x = 5
    val y = x.also { if (<warning descr="Condition is always true">it == 5</warning>) <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning> else <warning descr="[UNUSED_EXPRESSION] The expression is unused">2</warning> }
    if (<warning descr="Condition is always true">y == 5</warning>) {}
    true.also { println() }
}

fun inlineTakeIf(x: Int) {
    val x1 = x.takeIf { it > 0 }
    if (x1 != null && <warning descr="Condition is always true when reached">x1 > 0</warning>) {}
    val x2 = x.takeIf { it < 0 }?.takeIf { <warning descr="Condition is always true">it < 1</warning> }
    if (<warning descr="Condition is always true">x2 == null || <warning descr="Condition is always true when reached">x2 < 0</warning></warning>) {}
    val x3 = <warning descr="Value is always null">x.takeUnless { it > 0 }?.<warning descr="Value is always null">takeIf { <warning descr="Condition is always false">it > 0</warning> }</warning></warning>
    println(<weak_warning descr="Value is always null">x3</weak_warning>)
}

fun inlineTakeIfLet(s: String?) {
    val substring = s?.indexOf('a')?.takeIf { it > 0 }?.let { s.substring(0, it) }
    println(substring)
}

fun letWithUnit(s: String?) {
    if (s?.let {println(it)} != null) {}
}