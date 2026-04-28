// "Create property 'foo' as constructor parameter" "false"
// ERROR: Unresolved reference: foo
// K2_ERROR: Unresolved reference 'foo' on receiver of type 'ContainerForCreated'.
// K2_AFTER_ERROR: Unresolved reference 'foo' on receiver of type 'ContainerForCreated'.

class ContainerForCreated

fun useContainer(container: ContainerForCreated) {
    container.fo<caret>o()
}