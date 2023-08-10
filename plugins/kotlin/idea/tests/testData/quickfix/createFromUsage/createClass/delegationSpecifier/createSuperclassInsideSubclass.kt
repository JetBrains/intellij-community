// "Create class 'A'" "true"
package p

// TARGET_PARENT:
class Foo: <caret>A() {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix