import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class TestClassInitializers {

  def unknownField
  @Nullable nullableField
  @NotNull notnullField

  static unknownStaticField
  static @Nullable nullableStaticField
  static @NotNull notnullStaticField

  {
    unknownField = 1
    nullableField = unknownField
    notnullField = nullableField // ok, nullableField contains not null value
  }

  static {
    unknownStaticField = null
    nullableStaticField = unknownStaticField
    notnullStaticField = <warning descr="Expression 'nullableStaticField' might evaluate to null but is assigned to a variable that is annotated with @NotNull">nullableStaticField</warning>
  }
}
