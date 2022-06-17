import java.io.Serializable;

public class NotAnnotatedMultipleConstructors implements Serializable {

  private static final long serialVersionUID = 1L;

  final String myString;
  final Integer myInteger;
  final Boolean myBoolean;

  public <warning descr="Non-default constructor should be annotated with @PropertyMapping">NotAnnotatedMultipleConstructors</warning>(String string) {
    this(string, 0, false);
  }

  public <warning descr="Non-default constructor should be annotated with @PropertyMapping">NotAnnotatedMultipleConstructors</warning>(String string, Integer integer) {
    this(string, integer, false);
  }

  public <warning descr="Non-default constructor should be annotated with @PropertyMapping">NotAnnotatedMultipleConstructors</warning>(String string, Integer integer, Boolean bool) {
    myString = string;
    myInteger = integer;
    myBoolean = bool;
  }

}
