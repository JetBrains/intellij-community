// FIX: Remove explicit type arguments
// WITH_STDLIB

class Main {
    var x: List<Main> = listOf<Main>()
        set(value) {
            field = listOf<caret><Main>()
        }
}