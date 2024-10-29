package foo

class Foo

operator fun Foo.getValue(thisRef: Any?, property: Any?) = 10
operator fun Foo.setValue(thisRef: Any?, property: Any?, value: Int) {

}