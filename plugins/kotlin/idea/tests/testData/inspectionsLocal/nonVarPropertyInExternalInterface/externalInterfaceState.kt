// JS
// FIX: Change to 'var'

import react.State

external interface ExternalInterfaceWithPropertiesExtendingState : State {
    <caret>val pl: String
    var pr: String
}
