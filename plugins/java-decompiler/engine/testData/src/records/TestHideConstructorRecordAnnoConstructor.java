package records;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public record TestHideConstructorRecordAnnoConstructor(String b2) {
    @Anno
    public TestHideConstructorRecordAnnoConstructor(String b2) {
        this.b2 = b2;
    }
}

@Target({ElementType.CONSTRUCTOR})
@interface Anno {
}
