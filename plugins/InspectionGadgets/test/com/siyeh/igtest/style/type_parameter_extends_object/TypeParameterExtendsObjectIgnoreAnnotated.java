import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;

public class TypeParameterExtendsObjectIgnoreAnnotated<E extends @TypeParameterExtendsObjectIgnoreAnnotated.A Object> {
    @Target(ElementType.TYPE_USE)
    public @interface A{}

    public static final class Inner1<T extends <warning descr="Type parameter 'T' explicitly extends 'java.lang.Object'">Object</warning>> {}
    public static final class Inner2<T extends @A Object> {}
    public static final class Inner3<T extends List<Integer>> {}

    public <E extends <warning descr="Type parameter 'E' explicitly extends 'java.lang.Object'">Object</warning>>void foo(E e) {}
    public <E extends @A Object>void foo2(E e) {}
    public <E extends List>void foo3(E e) {}

    List<? extends @A Object> list;
    List<? extends <warning descr="Wildcard type argument '?' explicitly extends 'java.lang.Object'">Object</warning>> list2;
    List<? extends List> list3;
}
