package test

actual fun foo() {

}

fun use() {
    foo<caret>
}

// EXIST: { lookupString: foo, module: jvm }
// ABSENT: { lookupString: foo, module: common }