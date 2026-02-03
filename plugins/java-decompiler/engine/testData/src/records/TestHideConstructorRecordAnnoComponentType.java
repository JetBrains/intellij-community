package records;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Map;

public record TestHideConstructorRecordAnnoComponentType(Map<@Anno String, String> b2) {
}

@Target({ElementType.TYPE_USE})
@interface Anno {
}
