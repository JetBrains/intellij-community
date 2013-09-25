public enum E {
A,B;
}
public @interface Inter {
public abstract I[] bar() default {@I(2), @I(a = 5)};
public abstract java.lang.String[] strings() default {"a"};
public abstract E foo() default E.A;
}
public @interface I {
public abstract int a() default 4;
}
