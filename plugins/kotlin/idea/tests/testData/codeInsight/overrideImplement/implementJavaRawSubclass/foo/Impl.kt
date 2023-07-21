import foo.A

class C : A() {
    <caret>
}

// MEMBER_K2: "foo(x: List<String?>?, y: String?): B<String?>?"
// MEMBER_K1: "foo(x: (Mutable)List<(raw) Any?>!, y: String!): B<(raw) Any!>!"