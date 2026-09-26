import java.lang.Thread.currentThread

class B : Thread() {
    init {
        currentThread()
    }
}