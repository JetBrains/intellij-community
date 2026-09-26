// SET_TRUE: WRAP_COMMENTS
// RIGHT_MARGIN: 60

class WithAccessors {
    /**
     * A property description that is long enough to require wrapping.
     */
    var value: Int = 0
        /**
         * A getter description that is long enough to require wrapping.
         */
        get() = field
        /**
         * A setter description that is long enough to require wrapping.
         */
        set(new) {
            field = new
        }
}
