def singleQuoted() {
  if (<warning descr="Condition ''a' == null' is always false">'a' == null</warning>) {
  }
  if (<warning descr="Condition ''' == null' is always false">'' == null</warning>) {
  }
}

def tripleSingleQuoted() {
  if (<warning descr="Condition ''''fda''' == null' is always false">'''fda''' == null</warning>) {
  }
  if (<warning descr="Condition ''''''' == null' is always false">'''''' == null</warning>) {
  }
}

def doubleQuotedClosureInjection(a) {
  if (a == null) {
    "${<warning descr="Dereference of 'a' may produce 'java.lang.NullPointerException'">a</warning>.b}"
  }
  else {
    "${<warning descr="Qualifier 'a' is always not null">a</warning>?.b}"
  }
}

def doubleQuotedExpressionInjection(a) {
  if (a != null) {
    "$a.b"
  }
  else {
    "$<warning descr="Dereference of 'a' may produce 'java.lang.NullPointerException'">a</warning>.b"
  }
}

def tripleDoubleQuoted(a, b) {
  if (a == null) {
    """
$<warning descr="Dereference of 'a' may produce 'java.lang.NullPointerException'">a</warning>.b
"""
  }
  else {
    """
${<warning descr="Qualifier 'a' is always not null">a</warning>?.b}
"""
  }
}

def slashyString(a) {
  if (a == null) {
    /
$<warning descr="Dereference of 'a' may produce 'java.lang.NullPointerException'">a</warning>.b
    /
  }
  else {
    /
    ${<warning descr="Qualifier 'a' is always not null">a</warning>?.b}
    /
  }
}

def dollarSlashy(a) {
  if (a == null) {
    $/
    $<warning descr="Dereference of 'a' may produce 'java.lang.NullPointerException'">a</warning>.b
    /$
  }
  else {
    $/
    ${<warning descr="Qualifier 'a' is always not null">a</warning>?.b}
    /$
  }
}
