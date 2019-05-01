import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.xml.MarkupBuilder

class DynamicFeaturesExample {
    final ConfigObject config

    DynamicFeaturesExample(ConfigObject config) {
        this.config = config
    }

    @CompileDynamic
    def printXML() {
        new MarkupBuilder().records {
            persons.each { p ->
                person {
                    name(p.name)
                    age(p.age)
                }
            }
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    List getPersons() {
        config.persons
    }
}

static def getConfig() {
    def c = new ConfigObject()
    c.putAll(persons:[[name:'Alex', age: 30]])
    return c
}

static def addPerson(ConfigObject o) {
    o.persons.add([name:"Margaret", age:0])
}

def c = getConfig()
addPerson(c)

new DynamicFeaturesExample(c).printXML()
