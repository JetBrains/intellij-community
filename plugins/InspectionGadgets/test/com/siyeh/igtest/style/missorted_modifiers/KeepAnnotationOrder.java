import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class KeepAnnotationOrder {

  <warning descr="Missorted modifiers 'private @A @B @C final'">private @A @B @C<caret> final</warning> String foo = "";

  @Target(ElementType.FIELD) public @interface A {}
  @Target(ElementType.FIELD) public @interface B {}
  @Target(ElementType.FIELD) public @interface C {}
}