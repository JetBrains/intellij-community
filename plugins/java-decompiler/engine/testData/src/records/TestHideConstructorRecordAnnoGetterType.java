package records;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Map;

public record TestHideConstructorRecordAnnoGetterType(Map<@Anno String, String> b2) {
    public Map<String, @Anno String> b2() {
        return b2;
    }
}

@Target({ElementType.TYPE_USE})
@interface Anno {
}
