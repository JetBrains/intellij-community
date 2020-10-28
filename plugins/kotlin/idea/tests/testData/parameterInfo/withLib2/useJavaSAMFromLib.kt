// COMPILER_ARGUMENTS: -XXLanguage:-NewInference

package test


fun main() {
    p.JavaClass.takeSAM(<caret>)
}

/*
Text: (<highlight>((Int, String!) -> Unit)!</highlight>), Disabled: false, Strikeout: false, Green: false
Text: (<highlight>JavaSAM!</highlight>), Disabled: false, Strikeout: false, Green: false*/
