// "Create class 'A'" "true"
package p

class Foo: p.<caret>A(1, "2") {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix