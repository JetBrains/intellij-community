import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable


def testCastToPrimitive(@Nullable a) {
  <warning descr="Unboxing of '(double) a' may produce 'java.lang.NullPointerException'">(double) a</warning>
}

def testCastToPrimitiveBoolean(@Nullable a) {
  def b = (boolean) a
  if (<warning descr="Condition 'b == null' is always false">b == null</warning>) {

  }
}

def testCastToBoolean(@Nullable a) {
  def b = (Boolean) a
  if (b == null) {

  }
}

def testCastNotNull(@NotNull a) {
  def b = (String) a
  if (<warning descr="Condition 'b == null' is always false">b == null</warning>) {}
}
