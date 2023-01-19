val result: Int = 10

val foo: Int = re<before><caret>

fun otherPlace() {
    <change>
}

// TYPE: ""
// COMPLETION_TYPE: BASIC
// EXIST: result
