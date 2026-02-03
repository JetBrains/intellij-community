@interface SomeAnnotation {
    String value();
}

@SomeAnnotation (A.CONST)
interface A {
    String CONST = "42"
}

interface B {
}