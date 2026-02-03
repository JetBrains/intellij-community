class X {
  public static void main(String[] args) {
    new Foo(5) {
      public String toString(){return "";}
    }
  }
}