// IGNORE_K1
fun foo(n: Int) {
    <caret>if (n == 1) {
        print("1")
    }
    if (n == 2) {
        print("2")
    }
    print("always printed")
}