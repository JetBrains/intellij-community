fun square(arg: Int) = arg * arg

class Int {
    operator fun times(other: Int): Int
}