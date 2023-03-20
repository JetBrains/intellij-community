// EXPECT_VARIANT_IN_ORDER "class package3.MyClass"
// EXPECT_VARIANT_IN_ORDER "class package2.MyClass"
package root

class C: MyClass<caret>("1")
