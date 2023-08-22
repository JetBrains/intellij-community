package records;

import java.lang.annotation.*;

public record TestRecordAnno(@TA @RC int x, @M @P int y) {}

@Target(ElementType.TYPE_USE)
@interface TA {}

@Target(ElementType.RECORD_COMPONENT)
@interface RC {}

@Target(ElementType.METHOD)
@interface M {}

@Target(ElementType.PARAMETER)
@interface P {}
