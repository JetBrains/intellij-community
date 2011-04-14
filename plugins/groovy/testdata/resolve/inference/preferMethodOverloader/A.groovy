class Base {
    def getAt(def a) {
        "2"
    }
}
class Test extends Base {
    def getAt = {def a ->
        2
    }
}
def test = new Test()
test = test[2]
print tes<ref>t

