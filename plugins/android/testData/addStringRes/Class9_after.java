package p1.p2;

import android.content.res.Resources;
import android.content.Context;

public class Class extends Context {
  public void f(Resources resources) {
    String s = getResources().getString(R.string.hello);
  }
}