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
    <warning descr="Assignment to field 'i' defined in superclass 'AssignmentToSuperclassField'">i</warning> += 3;
    <warning descr="Assignment to field 'j' defined in superclass 'AssignmentToSuperclassField'">this.j</warning> = 4;
    <warning descr="Assignment to field 'k' defined in superclass 'AssignmentToSuperclassField'">super.k</warning>++;
    z = 100;
    Runnable r = () -> i = 6;
    new Object() {{
      i = 4;
    }};
  }
}