// "Replace with 'Anno'" "true"
package p1

import p2.Anno

@Deprecated("", ReplaceWith("Anno", "p2.Anno"))
annotation class Anno()

@<selection><caret></selection>Anno
class Foo
