package foo

class <caret>Impl : TestJavaInterface<String> {
}

// MEMBER_K2: "<T : String?> onTypingEvent(): T?"
// MEMBER_F10: "onTypingEvent(): T!"