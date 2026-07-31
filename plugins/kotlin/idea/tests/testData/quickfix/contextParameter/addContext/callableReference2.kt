// "Add context parameter to function" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// LANGUAGE_VERSION: 2.5
// K2_ERROR: NO_CONTEXT_ARGUMENT
class A<T> (val a: T){
    context(s: String)
    fun usesString(): T {
        print(s)
        return a
    }
}

fun foo(block: A<String>.() -> Unit) {
    val a = A("JetBrains")
    a.block()
}

fun main(args: Array<String>) {
    foo(A<String>::usesString<caret>)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix$ForEnclosingFunction