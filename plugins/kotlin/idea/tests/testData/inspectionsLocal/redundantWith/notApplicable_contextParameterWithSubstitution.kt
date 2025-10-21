// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xcontext-parameters

interface Context<T>

class C {
    companion object : Context<String>
}

fun foo() {
    wi<caret>th(C) {
        bar()
    }
}

context(s: Context<T>) fun <T> bar() {}

// IGNORE_K1