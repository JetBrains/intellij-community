class Factory {
  public static <T> List<T> createGenericList() {}
  public static List<Integer> createIntList() {}
  public static List<String> createStringList() {}
}

class Intermediate {

  List<String> s = create<caret>
}


