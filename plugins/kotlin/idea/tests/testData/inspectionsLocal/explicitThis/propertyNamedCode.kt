class A(private var code: String) {
    fun foo() {
        <caret>this.code
    }
}