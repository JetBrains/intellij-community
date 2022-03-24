import java.io.Serializable;
import com.intellij.serialization.PropertyMapping;

public class CorrectAnnotatedConstructor implements Serializable {

  private static final long serialVersionUID = 1L;

  final String myString;
  final Integer myInteger;
  final Boolean myBoolean;

  @PropertyMapping({"myString", "myInteger", "myBoolean"})
  public CorrectAnnotatedConstructor(String string, Integer integer, Boolean bool) {
    myString = string;
    myInteger = integer;
    myBoolean = bool;
  }
}
