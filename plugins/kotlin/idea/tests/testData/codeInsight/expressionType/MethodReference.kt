fun foo() {
}

fun bar() {
    run(::<caret>foo)
}

// K1_TYPE: ::foo -> <html>KFunction0&lt;Unit&gt;</html>

// K2_TYPE: ::foo -> <b>KFunction0&lt;Unit&gt;</b>
