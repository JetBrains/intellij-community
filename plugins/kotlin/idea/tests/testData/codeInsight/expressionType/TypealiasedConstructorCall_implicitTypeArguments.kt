package test

class MyClass

class Cell<T>(t: T)

class WithGenerics<T>(t: T)

typealias TypeAlias<TT> = WithGenerics<Cell<TT>>

fun test() {
    <caret>TypeAlias(Cell(MyClass()))
}

// K1_TYPE: TypeAlias(Cell(MyClass())) -> <html>TypeAlias&lt;MyClass&gt;<i> /* = WithGenerics&lt;Cell&lt;MyClass&gt;&gt; */</i></font></html>

// K2_TYPE: TypeAlias(Cell(MyClass())) -> TypeAlias&lt;MyClass&gt;
