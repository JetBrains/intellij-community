public enum E {
A,B;
}
public @interface Inter {
public I[] bar() default {@I(2), @I(a = 5)};
public java.lang.String[] strings() default {"a"};
public E foo() default E.A;
}
public @interface I {
public int a() default 4;
}
