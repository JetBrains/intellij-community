interface A {
    fun foo()
}
enum class JustKotlinEnum: A {
    JustFir<caret>stValue() {
        override fun foo() {}
    };

}