import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString
import groovy.transform.stc.SimpleType

Object fo<caret>o(@ClosureParams(value = FromString, options = ["java.lang.Integer,java.lang.String"]) Closure<? extends String> a) {
  bar(a)
}

def bar(@ClosureParams(value = FromString, options = ['Integer,String']) Closure<String> cl) {

}