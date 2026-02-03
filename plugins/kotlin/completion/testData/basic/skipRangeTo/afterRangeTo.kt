// REGISTRY: ide.completion.command.force.enabled true
package foo

class Test {
    fun a(){
        val b2 = 2
        val b = 3..<caret>
    }
}

// EXIST: {"lookupString":"b2"}