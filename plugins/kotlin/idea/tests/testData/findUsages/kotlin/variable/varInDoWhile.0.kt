// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "val message"


fun test() {
    do {
        val <caret>message = "test"
        println(message)
    } while (message.isEmpty())
}