// PSI_ELEMENT: org.jetbrains.kotlin.analysis.decompiled.light.classes.KtLightMethodForDecompiledDeclaration
// OPTIONS: overloadUsages
// PSI_ELEMENT_AS_TITLE: "fun foo(Int, Double, String): Unit"
package usages;

import library.Foo
class Usages {
    void foo() {
        Foo.foo<caret>();
    }

    void fooX() {
        Foo.foo(1);
    }

    void fooXY() {
        Foo.foo(1, 1.0);
    }

    void fooXYZ() {
        Foo.foo(1, 1.0, "1");
    }
}
