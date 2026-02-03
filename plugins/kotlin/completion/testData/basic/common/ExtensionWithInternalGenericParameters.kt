// FIR_IDENTICAL
// FIR_COMPARISON
import java.util.ArrayList

open class Base

fun <T: Base> List<List<T>>.extensionInternal() = 12

fun some() {
    ArrayList<List<Base>>().ex<caret>
}

// EXIST: extensionInternal