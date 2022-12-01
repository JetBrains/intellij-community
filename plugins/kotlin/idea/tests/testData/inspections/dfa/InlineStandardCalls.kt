// WITH_STDLIB
fun inlineLet() {
    val x = 5
    val y = x.let { if(<warning descr="Condition 'it == 5' is always true">it == 5</warning>) 1 else 2 }
    if (<warning descr="Condition 'y == 1' is always true">y == 1</warning>) {}
    val z = x.let { true }
    val z1 = x.let { false }
    println(<warning descr="Condition 'z && z1' is always false"><weak_warning descr="Value of 'z' is always true">z</weak_warning> && <weak_warning descr="Value of 'z1' is always false">z1</weak_warning></warning>)
}

fun inlineLetQuestion(x: Int?) {
    val res = x?.let { xx -> if (xx > 0) 1 else -1 } ?: 0
    if (res == 0 && <warning descr="Condition 'x == null' is always true when reached">x == null</warning>) {}
    if (res == 1 && x != null && <warning descr="Condition 'x > 0' is always true when reached">x > 0</warning>) {}
    if (res == -1 && x != null && <warning descr="Condition 'x <= 0' is always true when reached">x <= 0</warning>) {}
}

fun inlineAlso() {
    val x = 5
    val y = x.also { if (<warning descr="Condition 'it == 5' is always true">it == 5</warning>) <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning> else <warning descr="[UNUSED_EXPRESSION] The expression is unused">2</warning> }
    if (<warning descr="Condition 'y == 5' is always true">y == 5</warning>) {}
    true.also { println() }
}

fun inlineTakeIf(x: Int) {
    val x1 = x.takeIf { it > 0 }
    if (x1 != null && <warning descr="Condition 'x1 > 0' is always true when reached">x1 > 0</warning>) {}
    val x2 = x.takeIf { it < 0 }?.takeIf { <warning descr="Condition 'it < 1' is always true">it < 1</warning> }
    if (<warning descr="Condition 'x2 == null || x2 < 0' is always true">x2 == null || <warning descr="Condition 'x2 < 0' is always true when reached">x2 < 0</warning></warning>) {}
    val x3 = <warning descr="Value of 'x.takeUnless { it > 0 }?.takeIf { it > 0 }' is always null">x.takeUnless { it > 0 }?.<warning descr="Value of 'takeIf { it > 0 }' is always null">takeIf { <warning descr="Condition 'it > 0' is always false">it > 0</warning> }</warning></warning>
    println(<weak_warning descr="Value of 'x3' is always null">x3</weak_warning>)
}

fun inlineTakeIfLet(s: String?) {
    val substring = s?.indexOf('a')?.takeIf { it > 0 }?.let { s.substring(0, it) }
    println(substring)
}

fun letWithUnit(s: String?) {
    if (s?.let {println(it)} != null) {}
}

fun inlineRun() {
    var x = false
    var y = false

    for(line in 1..2) {
        line.run {
            if (<warning descr="Condition 'this == 3' is always false">this == 3</warning>) x = true else y = true
        }
    }
    println(<warning descr="Condition 'x && y' is always false"><weak_warning descr="Value of 'x' is always false">x</weak_warning> && y</warning>)
}

fun inlineAll() {
    val num = 123
    val res1 = <warning descr="Condition 'num.let { it > 10 }' is always true">num.let { <warning descr="Condition 'it > 10' is always true">it > 10</warning> }</warning>
    val res2 = num.also { println(<warning descr="Condition 'it > 10' is always true">it > 10</warning>) }
    val res3 = <warning descr="Condition 'num.run { this > 10 }' is always true">num.run { <warning descr="Condition 'this > 10' is always true">this > 10</warning> }</warning>
    val res4 = num.apply { println(<warning descr="Condition 'this > 10' is always true">this > 10</warning>) }
    println(<weak_warning descr="Value of 'res1' is always true">res1</weak_warning>)
    println(res2)
    println(<weak_warning descr="Value of 'res3' is always true">res3</weak_warning>)
    println(res4)
}
