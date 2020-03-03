import java.lang.annotation.*;

@Target(ElementType.TYPE_USE)
@interface SomeAnnotation {}

public class X {
  @SomeAnnotat<caret>ion
  public String method() {

  }
}

public class Y extends X {
  public java.lang.String method() {

  }
}