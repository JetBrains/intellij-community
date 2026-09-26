// PSI_ELEMENT: org.jetbrains.kotlin.analysis.decompiled.light.classes.KtLightMethodForDecompiledDeclaration
// OPTIONS: overloadUsages
package usages;

import library.Foo;
class Bar {
    {
        Foo.foo<caret>(2);
        Foo.foo();
    }
}
