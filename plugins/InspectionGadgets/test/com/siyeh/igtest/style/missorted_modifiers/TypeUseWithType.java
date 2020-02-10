import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class TypeUseWithType {

  <warning descr="Missorted modifiers '@TestAnnotation1 public'">@TestAnnotation1<caret>
  public</warning> String x() {
    return null;
  }

  @Target(value = { ElementType.TYPE_USE, ElementType.METHOD })
  public @interface TestAnnotation1 {
  }
}