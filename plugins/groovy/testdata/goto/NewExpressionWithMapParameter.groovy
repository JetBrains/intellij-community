class X {
  public X() {}
  public X(int i, int j){}
  public X(Map map){}
  int i;
  int j;
}

new X<caret>(i:1, j:2);
