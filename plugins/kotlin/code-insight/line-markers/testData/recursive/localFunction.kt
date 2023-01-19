fun outer(a: Int) {
    fun local(a: Int) {
        outer(a - 1)
        <lineMarker text="Recursive call">local</lineMarker>(0)
    }

    class Local {
        fun foo() {
            outer(1)
            <lineMarker text="Recursive call">foo</lineMarker>()
        }
    }

    val obj = object {
        fun bar() {
            outer(1)
            local(1)
            <lineMarker text="Recursive call">bar</lineMarker>()
        }
    }
}