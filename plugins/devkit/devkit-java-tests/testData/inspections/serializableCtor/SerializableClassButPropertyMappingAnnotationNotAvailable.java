import java.io.Serializable;

public class SerializableClassButPropertyMappingAnnotationNotAvailable implements Serializable {

  private static final long serialVersionUID = 1L;

  final String myString;
  final Integer myInteger;
  final Boolean myBoolean;

  public SerializableClassButPropertyMappingAnnotationNotAvailable(String string, Integer integer, Boolean bool) {
    myString = string;
    myInteger = integer;
    myBoolean = bool;
  }
}
