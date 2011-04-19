def a='string text'
char ch = a //here should not be inspection warning
int x = <warning descr="Cannot assign 'String' to 'int'">a</warning> //check that assignability inspection is on.
