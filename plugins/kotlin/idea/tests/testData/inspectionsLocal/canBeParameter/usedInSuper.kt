// FIX: Remove 'val' from parameter
open class Base1(s: String)

class UsedInSuper(<caret>val bar123: String) : Base1(bar123)