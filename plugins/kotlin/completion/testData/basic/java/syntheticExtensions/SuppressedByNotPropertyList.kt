import java.net.Socket

fun main(args: Array<String>) {
    val s = Socket()
    s.<caret>
}

// IGNORE_K2
// EXIST: {"lookupString":"getInputStream","tailText":"()","typeText":"InputStream!","attributes":"bold","allLookupStrings":"getInputStream","itemText":"getInputStream"}
// ABSENT: inputStream