package com.siyeh.igtest.assignment.assignment_to_superclass_field;

class AssignmentToSuperclassField {
  int i = 1;
  int j = 2;
  int k = 0;

  AssignmentToSuperclassField() {}

  AssignmentToSuperclassField(int i, int j, int k) {
    this.i = i;
    this.j = j;
    this.k = k;
  }
}
class B extends AssignmentToSuperclassField {
  int z;

  B() {
    i += 3;
    this.j = 4;
    super.k++;
    z = 100;
  }
}