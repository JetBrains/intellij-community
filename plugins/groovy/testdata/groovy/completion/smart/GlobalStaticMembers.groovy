class Super<T> { }
class SubString extends Super<String> { }
class SubInt extends Super<Integer> { }
class SubGeneric<T> extends Super<T> { }
class SubRaw extends Super { }

interface Constants {
  SubString SUBSTRING = null
}

class Factory {
  public static Object createObject() {}
  public static <T> Super<T> createExpected() {}
  public static Super<Integer> createSuperInt() {}
  public static SubInt createSubInt() {}
  public static <T> SubGeneric<T> createSubGeneric() {}
  public static final SubRaw SUB_RAW = null
  public static Super<String> NON_FINAL
  public final Super<String> NON_STATIC
}

class Intermediate {

  Super<String> s = <caret>
}


