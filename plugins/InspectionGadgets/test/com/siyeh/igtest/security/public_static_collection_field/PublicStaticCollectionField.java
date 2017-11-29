import java.util.*;

class PublicStaticCollectionField {

  public static List<String> <warning descr="'public static' collection field 'C', compromising security">C</warning> = new ArrayList();
  public static final List<String> D = Collections.unmodifiableList(Arrays.asList("bats", "rats", "cats"));
}