import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class IgnoreAnnotations {

  @TestAnnotation1 private final String foo = ""; // TYPE_USE annotation
  private final @TestAnnotation2 String bar = ""; // FIELD annotation
  private <warning descr="Missorted modifiers '@TestAnnotation3 final'">@TestAnnotation3</warning> final String baz = "";

  @Target(ElementType.TYPE_USE)
  public @interface TestAnnotation1 {
  }

  @Target({ElementType.FIELD})
  public @interface TestAnnotation2 {
  }

  @Target({ElementType.TYPE_USE, ElementType.FIELD})
  public @interface TestAnnotation3 {
  }
}