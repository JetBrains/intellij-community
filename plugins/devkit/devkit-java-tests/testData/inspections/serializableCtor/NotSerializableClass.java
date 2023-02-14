public class NotSerializableClass {

  final String myString;
  final Integer myInteger;
  final Boolean myBoolean;

  public NotSerializableClass(String string, Integer integer, Boolean bool) {
    myString = string;
    myInteger = integer;
    myBoolean = bool;
  }
}
