// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import org.intellij.lang.annotations.Pattern;

public class TestAssert {
  static {
    assert TestAssert.class.getName().length() > 0;
  }

  @Pattern("\\d+")
  public String simpleReturn() {
    return "-";
  }
}