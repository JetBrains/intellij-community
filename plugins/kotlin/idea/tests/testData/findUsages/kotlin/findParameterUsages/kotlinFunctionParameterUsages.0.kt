// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "t: T"
fun <T> foo(<caret>t: T): T {
    println(t)
    return t
}

fun bar(t: String) {
    print(t)
}

fun usage() {
    foo(t = ":)")
}

fun falseUsage() {
    bar(t = "")
}

