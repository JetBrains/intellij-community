package com.siyeh.igtest.style.unnecessarily_qualified_inner_class_access;
import java.util.Map;

@Y(UnnecessarilyQualifiedInnerClassAccess.X.class)
public class UnnecessarilyQualifiedInnerClassAccess<T> {

    public UnnecessarilyQualifiedInnerClassAccess(int i) {
        Map<caret>.Entry entry;
    }

    public UnnecessarilyQualifiedInnerClassAccess() {
        final String test =  UnnecessarilyQualifiedInnerClassAccess .Inner.TEST;
    }
    public static class Inner {
        public static final String TEST = "test";
    }

    void foo() {
        UnnecessarilyQualifiedInnerClassAccess<String>.X x; // no warning here, because generic parameter is needed
         UnnecessarilyQualifiedInnerClassAccess .Y<String> y;
    }

    class X {
        T t;
    }

    static class Y<T> {
        T t;
    }
}
@interface Y {
    Class value();
}

class Foo extends PresenterWidget<Foo.Bar> { // warning because Bar can be imported
    interface Bar extends View { }
}

interface View {}
class PresenterWidget<T>{}

class MultipleInheritance {
  interface I1 {
    interface V {
    }

    String FOO = "";
  }

  interface I2 {
    interface V {
    }

    String FOO = "";
  }

  static class C implements I1, I2 {
    public I1.V foo() {
      System.out.println(I1.FOO);
      return null;
    }
  }
}
@TestAnnotation(TestOuter.TestInner.TEST_FIELD)
class TestOuter {
  public interface TestInner {
    String TEST_FIELD = "TEST";
  }
}
@interface TestAnnotation {
  String value();
}

class HiearchyWithDefaults {

    public interface UnmodifiableCollection<E> extends Iterable<E> {
        boolean contains(E element);
        interface Defaults<E> {}

    }

    public interface UnmodifiableList<E> extends UnmodifiableCollection<E> {

        interface Decorator<E> extends Defaults<E> {
            @SuppressWarnings("RedundantMethodOverride")
            //IntellijIdea blooper: "method is identical to its super method" (and "redundant suppression")
            @Override
            default boolean contains(E element) {
                //IntellijIdea blooper: "Unnecessarily Qualified Inner Class Access"
                return UnmodifiableList.Defaults.super.contains(element);
            }

        }

        interface Defaults<E> extends UnmodifiableList<E>, UnmodifiableCollection.Defaults<E> {
            @Override
            default boolean contains(E element) {
                return false;
            }

        }
    }

}

/**
 * {@link java.util.concurrent.ConcurrentHashMap.SearchValuesTask}
 */
class InaccessibleClassReferencedInsideJavadocLink { }
