// "Implement as constructor parameters" "false"
// ERROR: Class 'End' is not abstract and does not implement abstract member public abstract val a: Int defined in IFoo
// ACTION: Apply all 'Add modifier' fixes in file
// ACTION: Create test
// ACTION: Implement members
// ACTION: Make 'End' 'abstract'
// ACTION: Rename file to End.kt
actual class <caret>End actual constructor(i: Int) : IFoo