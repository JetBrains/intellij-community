package ext;

import java.lang.annotation.*;

@Target(ElementType.PACKAGE)
public @interface PkgAnno {
  String value();
}
