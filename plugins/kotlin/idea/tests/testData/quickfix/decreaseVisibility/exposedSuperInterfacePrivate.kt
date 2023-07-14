// "Make 'Derived' private" "true"
// ACTION: Add full qualifier
// ACTION: Implement interface
// ACTION: Introduce import alias
// ACTION: Make 'Derived' internal
// ACTION: Make 'Derived' private
// ACTION: Make 'Outer' public

import Outer.Base

internal class Outer {
    interface Base
}

class Container {
    interface Derived : <caret>Base
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPrivateFix