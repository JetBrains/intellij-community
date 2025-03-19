fun printString(str: String) {
    str.run {/*<# this: String #>*/
        println(length)
    }
}