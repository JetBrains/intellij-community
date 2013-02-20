public class GenericTest {
    /**
    @see  com.intellij.openapi
    */
    public void a( GenTest<?> obj ) {

    }

    public void b( AnotherGenTest<?> obj ) {

    }

    public void c( Predicate<?> obj ) {

    }

    public void d( AnotherPredicate<?> obj ) {

    }

    public class GenTest<T> {

    }

    public class AnotherGenTest<T> {

    }

    public interface Predicate<T> {

    }

    public interface AnotherPredicate<T> {

    }

}
