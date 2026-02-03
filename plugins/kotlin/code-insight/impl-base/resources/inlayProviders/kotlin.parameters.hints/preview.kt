fun test() {
    someFun(/*<# name = #>*/"foo", /*<# value = #>*/42)
    /*<# block opt:start=hints.parameters.excluded #>*/
    listOf(/*<# …elements = #>*/"foo", /*<# p1: #>*/42)
    /*<# block opt:end,start=hints.parameters.compiled #>*/
    compiledFun(/*<# p0 = #>*/"foo", /*<# p1 = #>*/42)
    /*<# block opt:end #>*/
}

fun someFun(name: String, value: Int) {}