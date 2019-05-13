package pkg;

import java.lang.annotation.*;
import java.util.*;

class TypeAnnotations {
  @Target(ElementType.TYPE_USE)
  @interface TA { String value(); }

  @Target({ElementType.FIELD, ElementType.TYPE_USE})
  @interface MixA { String value(); }

  private @TA("field type") String f1;

  private @MixA("field and type") String f2;

  @TA("return type") int m1() {
    return 42;
  }

  void m2(@TA("parameter") int i) { }
}