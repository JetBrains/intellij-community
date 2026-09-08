// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual

interface Context

context(_<caret>: Context)
val prop: Int
    get() = 0

context(_: Context)
fun test() {
    print(::prop)
}
