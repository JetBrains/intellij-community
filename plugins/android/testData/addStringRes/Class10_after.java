package p1.p2.p3;

import android.content.Context;
import android.view.View;
import p1.p2.R;

public class Class extends View {
  public static void f(Context context) {
    String s = context.getResources().getString(R.string.hello);
  }
}