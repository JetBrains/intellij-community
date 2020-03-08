import java.lang.annotation.*;

@Target(ElementType.TYPE_USE)
@interface SomeAnnotation {}

public class X {
  public void method(@Som<caret>eAnnotation String x) {

  }
}

public class Y extends X {
  public void method(final @SomeAnnotation String x) {

  }
}