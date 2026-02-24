package pkg;

import java.lang.annotation.*;

class TestMemberAnnotations {
  @Retention(RetentionPolicy.RUNTIME)
  @interface A { String value() default ""; }

  @A("const") public static final int CONST = 42;
  @A("field") private int f;

  @A("return") private int f(@A("arg") int i) { return i + f + CONST; }
}