import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

class TypeUseAnnotation implements Cloneable {

  public @NonNull <caret>Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE_USE})
@interface NonNull {}