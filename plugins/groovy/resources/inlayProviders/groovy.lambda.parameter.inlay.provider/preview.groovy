import java.util.function.BiConsumer
import java.util.function.Consumer

def singleArg(Consumer<String> c) {}
def doubleArg(BiConsumer<Integer, String> c) {}

singleArg { /*<# String it ->  #>*/

}

singleArg { /*<# String  #>*/ arg ->

}

doubleArg { /*<# Integer  #>*/ a, /*<# String  #>*/ b ->

}