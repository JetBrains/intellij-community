import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString
import groovy.transform.stc.SimpleType

def fo<caret>o(a) {
  bar(a)
}

def bar(@ClosureParams(value = FromString, options = ['Integer,String']) Closure<String> cl) {

}