import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.enterprise.util.AnnotationLiteral;

public abstract class ExtendsAnnotation implements <warning descr="Class 'ExtendsAnnotation' implements annotation interface 'Override'">Override</warning> {

  interface J extends <warning descr="Interface 'J' extends annotation interface 'Override'">Override</warning> {}
}
class One extends AnnotationLiteral<Contains> implements Contains {
  @Override
  public String value() {
    return "test";
  }
}
@Documented
@Retention(value= RetentionPolicy.RUNTIME)
@interface Contains {
  String value() default "";
}