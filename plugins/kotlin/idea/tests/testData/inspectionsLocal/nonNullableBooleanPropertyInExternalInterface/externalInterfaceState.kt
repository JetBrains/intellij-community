// JS
// FIX: Convert to nullable type

import react.State

external interface ExternalInterfaceWithBooleanExtendingState : State {
    var bnl: Boolean?
    <caret>var bnn: Boolean
}
