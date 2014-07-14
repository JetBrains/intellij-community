package objects_require_non_null;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

class One {
  private String s;
  One(String s) {
    this.s = Objects.requireNonNull(s);
  }
}