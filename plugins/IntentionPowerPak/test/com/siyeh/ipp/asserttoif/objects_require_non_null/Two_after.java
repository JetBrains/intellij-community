package objects_require_non_null;

import java.util.Objects;

class Two {
  private Object o;

  Two(Object o) {
      this.o = Objects.requireNonNull(o);
  }
}