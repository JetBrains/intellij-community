import java.util.*;

class CollectionsFieldAccessReplaceableByMethodCall {

  void m() {
    System.out.println(<warning descr="'Collections.EMPTY_LIST' replaceable with 'Collections.emptyList()'">Collections.EMPTY_LIST</warning>);
    System.out.println(<warning descr="'Collections.EMPTY_MAP' replaceable with 'Collections.emptyMap()'">Collections.EMPTY_MAP</warning>);
    System.out.println(<warning descr="'Collections.EMPTY_SET' replaceable with 'Collections.emptySet()'">Collections.EMPTY_SET</warning>);
  }

  boolean test(Map<String, Object> map) {
    return map == Collections.EMPTY_MAP; // don't report object comparison
  }

}