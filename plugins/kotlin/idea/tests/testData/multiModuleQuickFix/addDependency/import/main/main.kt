// "Add dependency on module 'jvm'" "true"
// DISABLE_ERRORS
// FIR_COMPARISON
package bar

import bar.foo.Dependency<caret>

val q = Dependency()
// IGNORE_K1