// FIR_COMPARISON
// FIR_IDENTICAL
interface Foo {
    val foo: Int
    val bar: Int
}

class A(override val bar: Int, overrid<caret>): Foo

// EXIST: { lookupString: "override", itemText: "override"}
// EXIST: { itemText: "override val foo: Int", tailText: null, typeText: "Foo", attributes: "bold" }
// EXIST_JAVA_ONLY: { itemText: "override: Override", tailText: " (java.lang)" }
// EXIST_NATIVE_ONLY: { itemText:"overrideInit: ObjCObjectBase.OverrideInit" }
// EXIST_NATIVE_ONLY: { itemText:"override: ObjCSignatureOverride" }
// NOTHING_ELSE
