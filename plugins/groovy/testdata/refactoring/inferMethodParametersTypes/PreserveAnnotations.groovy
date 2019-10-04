void foo(@Anno @AnnoWithValueAndOptions(options = @Anno) @AnnoWithOptions(options = 1) a) {
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