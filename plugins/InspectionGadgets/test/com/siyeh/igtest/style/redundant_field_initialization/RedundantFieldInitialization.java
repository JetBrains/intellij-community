package com.siyeh.igtest.style.redundant_field_initialization;

class RedudantFieldInitialization {
  private int i = -0;
  private float f = .0f;
  private double d = 0.;
  private boolean e = false;
  private byte b = (byte)0;
  private String[] ss = null;
  private Object o = null;
  private String s = "";
  private short h = 1;

  public static final int DEFAULT_TYPE = 0;
  private int type = DEFAULT_TYPE;
  private int type2 = ((DEFAULT_TYPE) + 0);
}