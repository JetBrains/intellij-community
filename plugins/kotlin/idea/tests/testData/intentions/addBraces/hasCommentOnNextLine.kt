fun foo(list: List<String>) {
    if (true)<caret> println(list)

    // printing...
    println("hi")

    for (l in list) println(l)

    // printing...
// AFTER-WARNING: Parameter 'a' is never used
    println("hi")
}

fun println(a: Any) {}