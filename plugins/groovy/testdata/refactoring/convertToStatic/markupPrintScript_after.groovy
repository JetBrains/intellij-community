import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.xml.MarkupBuilder

@CompileStatic
class DynamicFeaturesExample {
    final ConfigObject config

    DynamicFeaturesExample(ConfigObject config) {
        this.config = config
    }

    @CompileDynamic
    String printXML() {
        new MarkupBuilder().records {
            persons.each { p ->
                person {
                    name(p.name)
                    age(p.age)
                }
            }
        }
    }

    @CompileDynamic
    List getPersons() {
        config.persons
    }
}

@CompileStatic
static ConfigObject getConfig() {
    def c = new ConfigObject()
    c.putAll(persons:[[name:'Alex', age: 30]])
    return c
}

static Object addPerson(ConfigObject o) {
    o.persons.add([name:"Margaret", age:0])
}

def c = getConfig()
addPerson(c)

new DynamicFeaturesExample(c).printXML()
