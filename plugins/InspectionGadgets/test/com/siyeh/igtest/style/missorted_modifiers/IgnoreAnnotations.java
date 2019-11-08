import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class IgnoreAnnotations {

  @TestAnnotation1 private final String foo = ""; // TYPE_USE annotation
  private final @TestAnnotation2 String bar = ""; // FIELD annotation
  <warning descr="Missorted modifiers 'private @TestAnnotation3 final'">private @TestAnnotation3 final</warning> String baz = "";

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