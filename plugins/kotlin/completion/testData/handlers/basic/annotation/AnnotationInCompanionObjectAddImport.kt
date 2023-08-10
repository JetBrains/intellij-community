// FIR_IDENTICAL
// FIR_COMPARISON
import java.lang.annotation.Inherited

class A {
    companion object {
        @Inh<caret>
    }
}

// ELEMENT: Inherited
