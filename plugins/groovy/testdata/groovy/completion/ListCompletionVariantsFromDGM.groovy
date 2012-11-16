List<String> foo() {}

use(Cat) {
  def strings = foo()
  strings.drop<caret>
}


class Cat {
  public static <T> List<T> drop(List<T> self, int num) {}
  public static <T> List<T> drop(Iterable<T> self, int num) {}

  public static <T> List<T> dropWhile(List<T> self, int num) {}
  public static <T> List<T> dropWhile(Iterable<T> self, int num) {}
}