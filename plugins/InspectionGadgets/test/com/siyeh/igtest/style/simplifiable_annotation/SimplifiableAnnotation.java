package com.siyeh.igtest.style.simplifiable_annotation;

public class SimplifiableAnnotation {

    @ SuppressWarnings(value = "blabla")
    @ Deprecated()
    Object foo() {
        return null;
    }
}
@interface ValueAnnotation {
  String[] value();
}
@interface ArrayAnnotation {
  String[] array();
}
@ValueAnnotation({"the value"})
@ArrayAnnotation(array = {"first"})
class MyClass {
}