// AFTER-WARNING: Parameter 'v' is never used
enum class A(<caret>v: Int) {
    E1(0), E2(1);

    fun foo() {

    }
}