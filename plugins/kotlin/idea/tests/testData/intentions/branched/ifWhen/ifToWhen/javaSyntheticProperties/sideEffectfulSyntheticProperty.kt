// WITH_STDLIB
// KTIJ-38076: a synthetic Java property with a side-effectful getter must not be folded into a `when` subject
fun test(h: CounterHolder) {
    i<caret>f (h.value == "a") println("a")
    else if (h.value == "b") println("b")
}
