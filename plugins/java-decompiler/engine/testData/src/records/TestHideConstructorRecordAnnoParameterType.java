package records;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Map;

public record TestHideConstructorRecordAnnoParameterType(Map<@Anno String, String> b2) {
    public TestHideConstructorRecordAnnoParameterType(Map<String, @Anno String> b2) {
        this.b2 = b2;
    }
}

@Target({ElementType.TYPE_USE})
@interface Anno {
}
