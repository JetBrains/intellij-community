def a='string text'
char ch = a //here should not be inspection warning
int <warning descr="Cannot assign 'String' to 'int'">x</warning> = a //check that assignability inspection is on.
