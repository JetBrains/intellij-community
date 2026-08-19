// SET_TRUE: WRAP_COMMENTS
// RIGHT_MARGIN: 60

val anonymous = object : Any() {
    /**
     * A description that is long enough to require wrapping
     * right about here.
     */
    fun member() {}
}

fun withLambda() {
    run {
        /**
         * A description that is long enough to require
         * wrapping right about here.
         */
        fun local() {}
    }
}
