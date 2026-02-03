@interface MyAnnotation {
  String value()
  String name() default ""
  int count() default 0
  Class<?> cls() default null
}

@MyAnnotation("abc")
class A1{}

@MyAnnotation(value = "ghi", name = "myName", count = 123, cls = String.class)
class A3{}