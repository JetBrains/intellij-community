// "Safe delete 'something'" "false"
// ACTION: Convert function to property
// ACTION: Convert member to extension
// ACTION: Convert to block body
// ACTION: Enable option 'Function return types' for 'Types' inlay hints
// ACTION: Go To Overridden Methods
// ACTION: Move to companion object
// ACTION: Specify return type explicitly


abstract class Abstract {
    open fun <caret>something() = "hi"
}
