println bv

def methodBeforeAssignment() {
  bv
}

<ELEMENT_UNDER_CARET><caret>bv</ELEMENT_UNDER_CARET> = 1

println <ELEMENT_UNDER_CARET>bv</ELEMENT_UNDER_CARET>

def methodAfterAssignment() {
  <ELEMENT_UNDER_CARET>bv</ELEMENT_UNDER_CARET>
}

<ELEMENT_UNDER_CARET>bv</ELEMENT_UNDER_CARET> = 2

println <ELEMENT_UNDER_CARET>bv</ELEMENT_UNDER_CARET>

def methodAfterReassignment() {
  <ELEMENT_UNDER_CARET>bv</ELEMENT_UNDER_CARET>
}
