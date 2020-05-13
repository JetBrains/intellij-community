import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class TypeUseWithType {

  public @TestAnnotation1 String x() {
    return null;
  }

  @Target(value = { ElementType.TYPE_USE, ElementType.METHOD })
  public @interface TestAnnotation1 {
  }
}