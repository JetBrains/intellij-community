import java.io.BufferedReader

class C : java.io.BufferedReader() {
    override fun hashCode(): Int {
        super<Buf<caret>>.readLine()
    }
}

// FIR_COMPARISON
// INVOCATION_COUNT: 2
// ELEMENT: BufferedReader