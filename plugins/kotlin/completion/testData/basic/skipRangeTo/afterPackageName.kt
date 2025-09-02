// REGISTRY: ide.completion.command.force.enabled true
package foo..<caret>

class Test {
    fun a(){
        val b = "b"
    }
}

// ABSENT: fun
// ABSENT: class