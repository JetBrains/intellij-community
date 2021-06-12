// "Change function signature to 'fun next(bits: Int): Int'" "true"
// RUNTIME_WITH_FULL_JDK
import java.util.Random

class MyRandom : Random() {
    <caret>override fun next(): Int = 4
}
