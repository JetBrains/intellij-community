package dependency_ambiguousConstructor

class Outer {
    inner class Inner {
        constructor(i: Int)
        constructor(s: String)
    }
}

typealias InnerTypeAlias = Outer.Inner
