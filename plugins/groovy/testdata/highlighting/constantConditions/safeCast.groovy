import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable


class A {}

class B {}

def unknown(A a) {
  def b = a as B
  if (b == null) {
    if (<warning descr="Condition 'a == null' is always true">a == null</warning>) {}
  }
}

def unknown2(A a) {
  def b = a as B
  if (a == null) {
    if (<warning descr="Condition 'b == null' is always true">b == null</warning>) {}
  }
}

def nullable(@Nullable A a) {
  def b = a as B
  if (b == null) {
    if (<warning descr="Condition 'a == null' is always true">a == null</warning>) {}
  }
}

def notNull(@NotNull A a) {
  def b = a as B
  if (<warning descr="Condition 'b == null' is always false">b == null</warning>) {
    if (a == null) {}
  }
}

def testAsBoolean(A a) {
  def b = a as Boolean
  if (<warning descr="Condition 'b == null' is always false">b == null</warning>) {

  }
}

def testNullableAsBoolean(@Nullable A a) {
  def b = a as Boolean
  if (<warning descr="Condition 'b == null' is always false">b == null</warning>) {

  }
}

def testAsPrimitiveBoolean(A a) {
  def b = a as boolean
  if (<warning descr="Condition 'b == null' is always false">b == null</warning>) {

  }
}

def testAsString(A a) {
  def b = a as String
  if (<warning descr="Condition 'b == null' is always false">b == null</warning>) {

  }
}

def testNullableAsString(@Nullable A a) {
  def b = a as String
  if (<warning descr="Condition 'b == null' is always false">b == null</warning>) {

  }
}
