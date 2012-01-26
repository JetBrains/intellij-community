package p1.p2;

import android.content.res.Resources;
import android.content.Context;

public class Class extends Context {
  public static void f(Context resources) {
    String s = resources.getString(R.string.hello);
  }
}