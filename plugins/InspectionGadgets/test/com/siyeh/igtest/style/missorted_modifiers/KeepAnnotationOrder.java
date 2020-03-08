import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class KeepAnnotationOrder {

  <warning descr="Missorted modifiers 'private @A @B @C'">private<caret></warning> @A @B @C final String foo = "";

  @Target(ElementType.FIELD) public @interface A {}
  @Target(ElementType.FIELD) public @interface B {}
  @Target(ElementType.FIELD) public @interface C {}
}