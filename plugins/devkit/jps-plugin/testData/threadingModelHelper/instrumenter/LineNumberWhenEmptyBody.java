package main;

import com.intellij.util.concurrency.annotations.fake.RequiresEdt;

public class LineNumberWhenEmptyBody {
  @RequiresEdt
  public void test() {}
}