// FIR_IDENTICAL
package foo

class <caret>Impl : TestJavaInterface {
}

// MEMBER: "onTextEvent(): Unit"
// MEMBER: "onAttachmentEvent(): Unit"
// MEMBER: "onEventDeleted(): Unit"
// MEMBER: "onSeenReceipt(): Unit"
// MEMBER: "onDeliveredReceipt(): Unit"
// MEMBER: "onTypingEvent(): Unit"