import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString
import groovy.transform.stc.SimpleType

Object fo<caret>o(@ClosureParams(value = SimpleType, options = ['? super java.lang.Integer', '? super java.lang.String']) Closure<String> a) {
  bar(a)
}

def bar(@ClosureParams(value = FromString, options = ['Integer,String']) Closure<String> cl) {

}