// JS
// FIX: Convert to nullable type

import react.Props

external interface ExternalInterfaceWithBooleanExtendingProps : Props {
    var bnl: Boolean?
    <caret>var bnn: Boolean
}
