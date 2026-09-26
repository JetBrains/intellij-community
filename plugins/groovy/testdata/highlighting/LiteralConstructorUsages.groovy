C c0 = []          // ok
C c1 = [1, "42"]   // ok
C c2 = <warning descr="Constructor 'C' in 'C' cannot be applied to '(java.lang.Integer)'">[1]</warning>
C c3 = <warning descr="Method call is ambiguous">["42", "42"]</warning>

Person p = <warning descr="Cannot apply default constructor for class 'Person'">[42]</warning>
LinkedList ll = [1, 2, 3, 4, 5]

class Container {
    class Inner {
        Inner(a, b) {}
    }
    Inner foo() {
        <warning descr="Constructor 'Inner' in 'Container.Inner' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">[1, 2]</warning>
    }
}

CollectionConstructor ccAmbiguous = <warning descr="Method call is ambiguous">["hi", "there"]</warning>
CollectionConstructor ccCollection = [1, 2, 3]
