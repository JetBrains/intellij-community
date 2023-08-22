public class SerializableClassButDoesNotContainSerialVersionUidField {

  final String myString;
  final Integer myInteger;
  final Boolean myBoolean;

  public SerializableClassButDoesNotContainSerialVersionUidField(String string, Integer integer, Boolean bool) {
    myString = string;
    myInteger = integer;
    myBoolean = bool;
  }
}
