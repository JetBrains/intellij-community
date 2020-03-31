import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

void foo(@ClosureParams(value = SimpleType, options = ['? super java.lang.Integer']) @Anno @AnnoWithValueAndOptions(options = @Anno) @AnnoWithOptions(options = 1) Closure<Integer> a) {
  a(2)
}

@interface Anno{}
@interface AnnoWithValueAndOptions{
  String value() default "groovy"
  Anno options()
}
@interface AnnoWithOptions {
  def options()
}

foo {it * 2}