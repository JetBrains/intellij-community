package com.siyeh.igtest.style.redundant_field_initialization;

class RedudantFieldInitialization {
  private int i = <warning descr="Field initialization to '-0' is redundant">-0</warning>;
  private float f = <warning descr="Field initialization to '.0f' is redundant">.0f</warning>;
  private double d = <warning descr="Field initialization to '0.' is redundant">0.</warning>;
  private boolean e = <warning descr="Field initialization to 'false' is redundant">false</warning>;
  private byte b = <warning descr="Field initialization to '(byte)0' is redundant">(byte)0</warning>;
  private String[] ss = <warning descr="Field initialization to 'null' is redundant">null</warning>;
  private Object o = <warning descr="Field initialization to 'null' is redundant">null</warning>;
  private String s = "";
  private short h = 1;

  private int pi = <warning descr="Field initialization to '(0)' is redundant">(0)</warning>;
  private float pf = <warning descr="Field initialization to '(0.0f)' is redundant">(0.0f)</warning>;
  private double pd = <warning descr="Field initialization to '(0.0)' is redundant">(0.0)</warning>;
  private boolean pe = <warning descr="Field initialization to '((false))' is redundant">((false))</warning>;
  private byte pb = <warning descr="Field initialization to '((0))' is redundant">((0))</warning>;
  private Object po = <warning descr="Field initialization to '((null))' is redundant">((null))</warning>;

  public static final int DEFAULT_TYPE = 0;
  private int type = DEFAULT_TYPE;
  private int type2 = ((DEFAULT_TYPE) + 0);
}
class InitializerOverwrite {

  {
    x1=42;
    x2=42;
  }
  int x1;
  int x2 = 0;

  public static void main(String... args) {
    InitializerOverwrite io = new InitializerOverwrite();
    System.out.println(io.x1); // 42
    System.out.println(io.x2); // 0
  }
}