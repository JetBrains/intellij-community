// IGNORE_K1
// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// Issue: KTIJ-30269

fun foo() {
    val a = "a"
    val b = "b"

    val k1  = $$"""<caret>
    ${'$'}a      //prints '${'$'}a'
    ${'$'}$a     //prints '${'$'}$a'
    $a           //prints '$a'
    $$a          //prints '10'
    $$$a         //prints '$10'
    $$$          //prints '$'
    ${b.length}  //prints '${b.length}'
    $${b.length} //prints '6'
    $$${b.length}//prints '$6'
"""
}