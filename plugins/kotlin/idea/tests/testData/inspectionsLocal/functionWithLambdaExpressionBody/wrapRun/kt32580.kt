// FIX: Convert to run { ... }
// WITH_STDLIB
class C {
    fun f4() = {<caret>
        "single-expression function which returns lambda"
    }
}