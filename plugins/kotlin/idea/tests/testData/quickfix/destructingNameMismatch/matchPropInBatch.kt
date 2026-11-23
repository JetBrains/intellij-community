// "Fix all 'Declaration name mismatch in destructuring declaration' problems in file" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

data class Person(val firstName: String, val lastName: String)

fun test(person: Person) {
    val (fi<caret>rst , last) = person
    val (one , two) = person
}

data class Point(val x: Int, val y: Int)

data class Point3D(val x: Int, val y: Int, val z: Int)

fun skippedLast(p: Point): Any {
    val (x1: Any, _) = p
    return x1
}

fun skippedFirst(p: Point): Int {
    val (_, y1) = p
    return y1
}

fun skippedMiddle(p: Point3D): Int {
    val (x1, _, z1) = p
    return x1 + z1
}

// FUS_QUICKFIX_NAME: com.intellij.codeInspection.actions.CleanupInspectionIntention