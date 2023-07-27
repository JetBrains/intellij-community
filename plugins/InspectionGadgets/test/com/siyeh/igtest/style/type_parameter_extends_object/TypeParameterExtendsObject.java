import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;

public class TypeParameterExtendsObject<E extends <warning descr="Type parameter 'E' explicitly extends 'java.lang.Object'">@TypeParameterExtendsObject.A Object</warning>> {
    @Target(ElementType.TYPE_USE)
    public @interface A{}

    public static final class Inner1<T extends <warning descr="Type parameter 'T' explicitly extends 'java.lang.Object'">Object</warning>> {}
    public static final class Inner2<T extends <warning descr="Type parameter 'T' explicitly extends 'java.lang.Object'">@A Object</warning>> {}
    public static final class Inner3<T extends List<Integer>> {}

    public <E extends <warning descr="Type parameter 'E' explicitly extends 'java.lang.Object'">Object</warning>>void foo(E e) {}
    public <E extends <warning descr="Type parameter 'E' explicitly extends 'java.lang.Object'">@A Object</warning>>void foo2(E e) {}
    public <E extends List>void foo3(E e) {}

    List<? extends <warning descr="Wildcard type argument '?' explicitly extends 'java.lang.Object'">@A Object</warning>> list;
    List<? extends <warning descr="Wildcard type argument '?' explicitly extends 'java.lang.Object'">Object</warning>> list2;
    List<? extends List> <error descr="Variable 'list2' is already defined in the scope">list2</error>;
}
