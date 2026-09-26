import java.io.Serializable;

public class NotAnnotatedConstructor implements Serializable {

  private static final long serialVersionUID = 1L;

  final String myString;
  final Integer myInteger;
  final Boolean myBoolean;

  public <warning descr="Non-default constructor should be annotated with @PropertyMapping">NotAnnotatedConstructor</warning>(String string, Integer integer, Boolean bool) {
    myString = string;
    myInteger = integer;
    myBoolean = bool;
  }
}
