// "Create class 'A'" "true"
package p

class X {

}

class Foo: X.<caret>A(1, "2") {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix