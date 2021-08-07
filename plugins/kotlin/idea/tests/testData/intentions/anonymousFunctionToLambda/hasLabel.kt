fun foo(f: () -> Int) {
    f()
}

inline fun a(block: () -> String) = block()

fun main() {
    foo(<caret>fun(): Int {
        a foo@{
            a block1@{
                return 42
            }
        }
        val a = 1
        if (a > 1) {
            return 1
        }
        return 2
    })
}