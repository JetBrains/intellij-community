import foo.Intf

class Impl(): Intf {
    <caret>
}

// MEMBER_K2: "getFooBar(): String?"
// MEMBER_K1: "getFooBar(): String!"