import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;

public class TypeParameterExtendsObject<<warning descr="Type parameter 'E' explicitly extends 'java.lang.Object'">E</warning> extends Object> {

    public <<warning descr="Type parameter 'E' explicitly extends 'java.lang.Object'">E</warning> extends Object>void foo(E e)
    {

    }
    public <E extends List>void foo2(E e)
    {

    }

    @Target(ElementType.TYPE_USE)
    @interface A{}

    List<? extends @A Object> list;

    List<<warning descr="Wildcard type argument '?' explicitly extends 'java.lang.Object'">?</warning> extends Object> list2;
}
