// "Safe delete 'NamedObject'" "true"
import TestClass.NamedObject

class TestClass{
    private object NamedObject<caret> {
        const val CONST = "abc"
    }
}
// IGNORE_K2