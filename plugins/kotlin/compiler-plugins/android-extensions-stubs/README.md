This module contains stubs for several classes from Android SDK on which the Android Extensions runtime library has dependencies.

In practice, absence of these classes won't make a huge difference: both compiler and IDE plugins use classes from the `kotlinx.android`
  package only for getting their qualified name (instead of hard-coding it in a string constant). No deep introspection is made.

However, the IntelliJ plugin verifier issues an error if any class reference in bytecode is unresolved.

This module should be removed as we remove the Android Extensions support itself.