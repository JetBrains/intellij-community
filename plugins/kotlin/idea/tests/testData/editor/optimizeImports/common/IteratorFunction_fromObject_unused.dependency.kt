package test1

class MyClass

interface MyIteratorProvider {
    operator fun MyClass.iterator(): Iterator<MyClass> = TODO()
}

object MyObject : MyIteratorProvider