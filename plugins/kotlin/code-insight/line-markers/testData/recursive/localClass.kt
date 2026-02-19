fun f(a: Int) {
    class Local {
        fun member() {
            f(1)
        }
    }
}
