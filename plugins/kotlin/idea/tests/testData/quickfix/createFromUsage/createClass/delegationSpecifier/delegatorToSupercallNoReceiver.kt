// "Create class 'A'" "true"
package p

class Foo: <caret>A(1, "2") {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix