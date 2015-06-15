class ArrayObjectsEquals {

  boolean one(String[] ss1, String[] ss2) {
    return java.util.Objects.<warning descr="'Objects.equals()' on arrays should probably be 'Arrays.equals()'">equals</warning>(ss1, ss2);
  }

  boolean two(String[][] ss1, String[][] ss2) {
    return java.util.Objects.<warning descr="'Objects.equals()' on arrays should probably be 'Arrays.deepEquals()'">equals</warning>(ss1, ss2);
  }

  boolean noWarn(String s1, String[] ss2) {
    return java.util.Objects.equals(s1, ss2);
  }
}