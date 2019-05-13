def a='string text'
char <warning descr="Cannot assign 'String' to 'char'">ch</warning> = a
int <warning descr="Cannot assign 'String' to 'int'">x</warning> = a //check that assignability inspection is on.
