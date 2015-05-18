def trueConstantConditions() {
  def a = true
  if (<warning descr="Condition 'a' is always true">a</warning>) {}
  if (<warning descr="Condition '!a' is always false">!a</warning>) {}

  if (<warning descr="Condition 'a == true' is always true">a == true</warning>) {}
  if (<warning descr="Condition 'a != true' is always false">a != true</warning>) {}
  if (<warning descr="Condition 'a == false' is always false">a == false</warning>) {}
  if (<warning descr="Condition 'a != false' is always true">a != false</warning>) {}
  if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
  if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
  if (<warning descr="Condition 'a == 10' is always false">a == 10</warning>) {}
  if (<warning descr="Condition 'a != 10' is always true">a != 10</warning>) {}

  if (<warning descr="Condition '!a == true' is always false">!a == true</warning>) {}
  if (<warning descr="Condition '!a != true' is always true">!a != true</warning>) {}
  if (<warning descr="Condition '!a == false' is always true">!a == false</warning>) {}
  if (<warning descr="Condition '!a != false' is always false">!a != false</warning>) {}
  if (<warning descr="Condition '!a == null' is always false">!a == null</warning>) {}
  if (<warning descr="Condition '!a != null' is always true">!a != null</warning>) {}
}

def falseConstantConditions() {
  def a = false
  if (<warning descr="Condition 'a' is always false">a</warning>) {}
  if (<warning descr="Condition '!a' is always true">!a</warning>) {}

  if (<warning descr="Condition 'a == true' is always false">a == true</warning>) {}
  if (<warning descr="Condition 'a != true' is always true">a != true</warning>) {}
  if (<warning descr="Condition 'a == false' is always true">a == false</warning>) {}
  if (<warning descr="Condition 'a != false' is always false">a != false</warning>) {}
  if (<warning descr="Condition 'a == null' is always false">a == null</warning>) {}
  if (<warning descr="Condition 'a != null' is always true">a != null</warning>) {}
  if (<warning descr="Condition 'a == 10' is always false">a == 10</warning>) {}
  if (<warning descr="Condition 'a != 10' is always true">a != 10</warning>) {}

  if (<warning descr="Condition '!a == true' is always true">!a == true</warning>) {}
  if (<warning descr="Condition '!a != true' is always false">!a != true</warning>) {}
  if (<warning descr="Condition '!a == false' is always false">!a == false</warning>) {}
  if (<warning descr="Condition '!a != false' is always true">!a != false</warning>) {}
  if (<warning descr="Condition '!a == null' is always false">!a == null</warning>) {}
  if (<warning descr="Condition '!a != null' is always true">!a != null</warning>) {}
}

def nullConstantConditions() {
  def a = null
  if (<warning descr="Condition 'a' is always false">a</warning>) {}
  if (<warning descr="Condition '!a' is always true">!a</warning>) {}

  if (<warning descr="Condition 'a == true' is always false">a == true</warning>) {}
  if (<warning descr="Condition 'a != true' is always true">a != true</warning>) {}
  if (<warning descr="Condition 'a == false' is always false">a == false</warning>) {}
  if (<warning descr="Condition 'a != false' is always true">a != false</warning>) {}
  if (<warning descr="Condition 'a == null' is always true">a == null</warning>) {}
  if (<warning descr="Condition 'a != null' is always false">a != null</warning>) {}
  if (<warning descr="Condition 'a == 10' is always false">a == 10</warning>) {}
  if (<warning descr="Condition 'a != 10' is always true">a != 10</warning>) {}

  if (<warning descr="Condition '!a == true' is always true">!a == true</warning>) {}
  if (<warning descr="Condition '!a != true' is always false">!a != true</warning>) {}
  if (<warning descr="Condition '!a == false' is always false">!a == false</warning>) {}
  if (<warning descr="Condition '!a != false' is always true">!a != false</warning>) {}
  if (<warning descr="Condition '!a == null' is always false">!a == null</warning>) {}
  if (<warning descr="Condition '!a != null' is always true">!a != null</warning>) {}
}
