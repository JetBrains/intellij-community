package unqualified_inner_class_access;

class Deep {
  public static class One {
    public static class Two {
      public static class Three {}
    }
  }
}
class User {
  public static void main(String[] args) {
    new Deep.One.Two.Three();
  }
}