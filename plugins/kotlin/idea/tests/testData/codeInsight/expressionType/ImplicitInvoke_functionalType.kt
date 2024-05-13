fun test(foo: () -> String) {
    fo<caret>o()
}

// K1_TYPE: foo -> <html>() -&gt; String</html>
// K1_TYPE: foo() -> <html>String</html>

// K2_TYPE: foo -> () -&gt; String
// K2_TYPE: foo() -> String
