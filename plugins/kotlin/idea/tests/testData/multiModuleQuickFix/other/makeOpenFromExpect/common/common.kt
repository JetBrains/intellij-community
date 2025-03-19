// "Make 'One' 'open'" "true"
// ERROR: Expected class 'Two' has no actual declaration in module testModule_JVM for JVM
// IGNORE_K2

expect class One
expect class Two : <caret>One