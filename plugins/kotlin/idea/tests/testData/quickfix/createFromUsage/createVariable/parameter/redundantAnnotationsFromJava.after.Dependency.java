import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.*;
import java.util.List;


@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
@interface MyNotNull {
    String value() default "";

    Class<? extends Exception> exception() default Exception.class;
}

public class A {
    @NotNull
    String foo23;

    List<@MyNotNull @NotNull Integer> bar23;
    A(@NotNull String foo23, List<@Nullable List<@MyNotNull @NotNull Integer>> bar23) {
        this.foo23 = foo23;
        this.bar23 = bar23;
    }
}