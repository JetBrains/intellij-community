import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

class TypeUseAnnotation implements Cloneable {

  public @NonNull TypeUseAnnotation clone() throws CloneNotSupportedException {
      return (TypeUseAnnotation) super.clone();
  }
}
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE_USE})
@interface NonNull {}