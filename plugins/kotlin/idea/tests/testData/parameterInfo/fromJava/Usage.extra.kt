package p

@Target(AnnotationTarget.TYPE)
annotation class MyAnnotation

class Foo(bar: @MyAnnotation String)