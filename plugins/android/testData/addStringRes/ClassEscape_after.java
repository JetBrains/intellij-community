package p1.p2;

import android.content.Context;

public class ClassEscape {
  public static void f(Context context) {
    String s = context.getString(R.string.hello);
  }
}