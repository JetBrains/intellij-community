// WITH_STDLIB
// PROBLEM: Java collection 'ArrayBlockingQueue' is parameterized with a nullable type
// FIX: none
import java.util.concurrent.ArrayBlockingQueue

class MyQueue<T> {
    val queue = Array<caret>BlockingQueue<T>(1)
}