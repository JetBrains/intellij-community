import foo.Intf

class Impl(): Intf {
    <caret>
}

// MEMBER_K2: "fooBar(i: Int, s: Array<out String?>?, foo: Any?): Unit"
// MEMBER_K1: "fooBar(i: Int, s: Array<(out) String!>!, foo: Any!): Unit"