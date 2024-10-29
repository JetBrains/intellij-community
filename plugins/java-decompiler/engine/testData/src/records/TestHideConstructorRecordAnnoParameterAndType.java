package records;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Map;

public record TestHideConstructorRecordAnnoParameterAndType(@Anno Map<String, String> b2) {
}

@Target({ElementType.TYPE_USE, ElementType.PARAMETER})
@interface Anno {
}
