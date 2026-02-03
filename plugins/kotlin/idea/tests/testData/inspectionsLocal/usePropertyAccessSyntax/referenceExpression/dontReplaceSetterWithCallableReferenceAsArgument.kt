// PROBLEM: none
import java.util.concurrent.Callable

class K: J() {

    fun doSth() {
        <caret>setX(this::getX)
    }
}