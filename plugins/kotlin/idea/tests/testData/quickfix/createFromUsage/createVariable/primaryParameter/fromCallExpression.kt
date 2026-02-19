// "Create property 'foo' as constructor parameter" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: Unresolved reference 'foo'.

class ContainerForCreated

fun useContainer(container: ContainerForCreated) {
    container.fo<caret>o()
}