package p1.p2.p3;

import android.content.Context;
import android.content.res.Resources;
import p1.p2.R;

public class Class extends Context {
  public void f(Resources resources) {
    String s = resources.getString(R.string.hello);
  }
}