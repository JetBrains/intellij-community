fun foo(p: java.io.File?){ }

fun bar(o: Any){
    foo(o as? <caret>)
}
// AUTOCOMPLETE_SETTING: true
// IGNORE_K2
