// PSI_ELEMENT: org.jetbrains.kotlin.analysis.decompiled.light.classes.KtLightMethodForDecompiledDeclaration
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun foo(): Unit"
package usages;

import a.Outer;

class X extends Outer.Inner {
    {
        new Outer.Inner().foo<caret>();
    }
}
