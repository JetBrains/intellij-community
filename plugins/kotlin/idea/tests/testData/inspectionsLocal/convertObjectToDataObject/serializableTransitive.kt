// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
import java.io.Serializable

object<caret> Foo : Bar()

open class Bar : Serializable
