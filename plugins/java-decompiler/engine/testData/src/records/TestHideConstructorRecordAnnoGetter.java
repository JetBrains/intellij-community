package records;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public record TestHideConstructorRecordAnnoGetter(String b2) {
    @Anno
    public String b2() {
        return b2;
    }
}

@Target({ElementType.METHOD})
@interface Anno {
}
