// "Add context parameter to function" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: NO_CONTEXT_ARGUMENT
package app

import lib.Logger
import lib.logged

context(_: Logger)
fun caller() {
    logged()
}