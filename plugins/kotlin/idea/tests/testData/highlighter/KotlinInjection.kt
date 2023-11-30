// IGNORE_K2
// EXPECTED_DUPLICATED_HIGHLIGHTING
package language_injection

<symbolName descr="null">import</symbolName> org.intellij.lang.annotations.<symbolName descr="null">Language</symbolName>

<symbolName descr="null">@Language</symbolName>("kotlin")
val <symbolName descr="null">test</symbolName> = "<inject descr="null">fun test2() {}</inject>"
