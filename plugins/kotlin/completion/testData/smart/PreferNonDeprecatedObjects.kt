open class Base

object NonDeprecatedSingleton : Base()

@Deprecated("Use NonDeprecatedSingleton instead")
object DeprecatedSingleton : Base()

val element: Base = <caret>

// WITH_ORDER
// EXIST: NonDeprecatedSingleton
// EXIST: DeprecatedSingleton
