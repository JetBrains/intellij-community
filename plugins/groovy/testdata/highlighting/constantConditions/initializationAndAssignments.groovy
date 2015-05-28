import org.jetbrains.annotations.Nullable
import org.jetbrains.annotations.NotNull

def nullInitialization() {
  @NotNull b = <warning descr="'null' is assigned to a variable that is annotated with @NotNull">null</warning>
}

def nullableInitialization(@Nullable a) {
    @NotNull b = <warning descr="Expression 'a' might evaluate to null but is assigned to a variable that is annotated with @NotNull">a</warning>
}

def unknownInitialization(a) {
  @NotNull b = a
}

def nullAssignment() {
  @NotNull b
  b = <warning descr="'null' is assigned to a variable that is annotated with @NotNull">null</warning>
}

def nullableAssignment(@Nullable a) {
    @NotNull b
    b = <warning descr="Expression 'a' might evaluate to null but is assigned to a variable that is annotated with @NotNull">a</warning>
}

def unknownAssignment(a) {
  @NotNull b
  b = a
}

def "assign not-null to nullable"(@NotNull a) {
  @Nullable b = a
  @NotNull c = b
}

def "assign nullable to unknown"() {
  def a = null
  @NotNull b = <warning descr="Expression 'a' might evaluate to null but is assigned to a variable that is annotated with @NotNull">a</warning>
}


def unknownAssignmentToPrimitive(a) {
    int i = a
    char c = a
}

def nullableAssignmentToPrimitive(@Nullable a) {
    int i = <warning descr="Unboxing of 'a' may produce 'java.lang.NullPointerException'">a</warning>
}

def notNullAssignmentToPrimitive(@NotNull a) {
    int i = a
}

def nullableAssignmentToBoolean(@Nullable a) {
    boolean b = a   // ok
}

def nullableAssignmentToNonPrimitive(@Nullable a) {
  Integer i = a   // ok
}

int intMethod() {1}

@Nullable
Integer nullableIntegerMethod() {null}

def unknownMethod() {}

def intMethodCallToPrimitive() {
  int i = intMethod()
}

def intMethodCallToWrapper() {
  Integer i = intMethod()
  if (<warning descr="Condition 'i == null' is always false">i == null</warning>) {}
}

def unknownMethodCallToBoolean() {
  boolean b = unknownMethod()
}

def unknownMethodCallToBooleanWrapper () {
  Boolean b = unknownMethod()
  if (b == null) {}
}

def nullableIntegerMethodToPrimitive() {
  int i = <warning descr="Unboxing of 'nullableIntegerMethod()' may produce 'java.lang.NullPointerException'">nullableIntegerMethod()</warning>
}


