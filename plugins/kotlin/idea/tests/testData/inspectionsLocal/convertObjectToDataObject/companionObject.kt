// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
import java.io.Serializable

class Foo {
    companion <caret>object : Serializable
}
