public class ClassContainingSerialVersionUidFieldButIsNotSerializable {

  private static final long serialVersionUID = 1L;

  final String myString;
  final Integer myInteger;
  final Boolean myBoolean;

  public ClassContainingSerialVersionUidFieldButIsNotSerializable(String string, Integer integer, Boolean bool) {
    myString = string;
    myInteger = integer;
    myBoolean = bool;
  }
}
