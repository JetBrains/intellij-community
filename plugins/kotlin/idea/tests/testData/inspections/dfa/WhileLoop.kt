// WITH_STDLIB
fun simpleWhile() {
    var x = 0
    while (x < 100000) {
        x += 2
        if (<warning descr="Condition 'x == 11' is always false">x == 11</warning>) {}
    }
}
fun withBreak() {
    var x = 0
    while (true) {
        x++
        if (x > 5) break
    }
    if (<warning descr="Condition 'x < 6' is always false">x < 6</warning>) { }
}
fun withContinue() {
    var x = 0
    while (true) {
        ++x
        if (x > 5) continue
        if (<warning descr="Condition 'x < 6' is always true">x < 6</warning>) { }
    }
    <warning descr="[UNREACHABLE_CODE] Unreachable code">if (x < 6) { }</warning>
}
fun doWhile(x: Int) {
    var y = 1
    do {
        ++y
    } while(y < x)
    if (<warning descr="Condition 'y >= x' is always true">y >= x</warning>) {}
}