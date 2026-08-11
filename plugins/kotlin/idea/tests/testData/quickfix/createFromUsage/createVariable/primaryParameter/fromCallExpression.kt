// "Create property 'foo' as constructor parameter" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

class ContainerForCreated

fun useContainer(container: ContainerForCreated) {
    container.fo<caret>o()
}