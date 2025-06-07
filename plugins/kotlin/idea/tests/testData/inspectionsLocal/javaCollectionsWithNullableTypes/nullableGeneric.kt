// WITH_STDLIB
import java.util.concurrent.ArrayBlockingQueue

class MyQueue<T> {
    val queue = ArrayBlockingQueue<T?<caret>>(1)
}