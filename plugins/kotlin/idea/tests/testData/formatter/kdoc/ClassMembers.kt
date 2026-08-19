// SET_TRUE: WRAP_COMMENTS
// RIGHT_MARGIN: 60

class Outer(val a: Int) {
    /**
     * A member property description long enough to require wrapping.
     */
    val member: Int = 1

    /**
     * A member function description long enough to require wrapping.
     */
    fun memberFunction() {}

    /**
     * A secondary constructor description long enough to require wrapping.
     */
    constructor() : this(0)

    /**
     * A nested class description long enough to require wrapping.
     */
    class Nested

    /**
     * A companion description long enough to require wrapping.
     */
    companion object {
        /**
         * A companion member description long enough to require wrapping.
         */
        fun companionFunction() {}
    }
}
