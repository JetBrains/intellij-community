package library;

public @interface OldAnnotation {
  int oldParam() default 0;

  int recentParam() default 0;
}