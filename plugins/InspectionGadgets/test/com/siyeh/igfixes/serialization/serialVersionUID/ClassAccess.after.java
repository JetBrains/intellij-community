import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public class ClassAccess implements Serializable {

    private static final long serialVersionUID = 2269507042140353173L;

    enum TestEnum {}

  // at one point javac generated a synthetic field for .class accesses with a name starting with the string "class$"
  Map<TestEnum, Set<String>> testField = new EnumMap<TestEnum, Set<String>>(TestEnum.class);
  //    Map<TestEnum, Set<String>> testField = new HashMap<>();

}