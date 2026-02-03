class X {
  public static void main(String[] args) {
    new Foo() {
      public String toString(){return "";}
    }
  }
}