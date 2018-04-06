import groovy.xml.MarkupBuilder

class DynamicFeaturesExample {
    final ConfigObject config

    DynamicFeaturesExample(ConfigObject config) {
        this.config = config
    }

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
