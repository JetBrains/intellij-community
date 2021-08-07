import java.util.*;

class CollectionsFieldAccessReplaceableByMethodCall {

  void m() {
    System.out.println(<warning descr="'Collections.EMPTY_LIST' can be replaced with 'Collections.emptyList()'">Collections.EMPTY_LIST</warning>);
    System.out.println(<warning descr="'Collections.EMPTY_MAP' can be replaced with 'Collections.emptyMap()'">Collections.EMPTY_MAP</warning>);
    System.out.println(<warning descr="'Collections.EMPTY_SET' can be replaced with 'Collections.emptySet()'">Collections.EMPTY_SET</warning>);
  }

  boolean test(Map<String, Object> map) {
    return map == Collections.EMPTY_MAP; // don't report object comparison
  }

}