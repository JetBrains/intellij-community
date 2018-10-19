package com.siyeh.igtest.encapsulation.public_field;

import org.jetbrains.annotations.Nullable;

public class PublicField {
  public final X y = X.Y;
  public String <warning descr="'public' field 's'">s</warning> = "";
  @Nullable public String t = "";
  public static final String LEGAL = "legal";


  public enum X {
    Y
  }
}
