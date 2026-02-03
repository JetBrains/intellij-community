class Base {
    def getAt(def a) {
        2
    }
}
class Test extends Base {
    def plus = {int a ->
        "a"
    }

    def plus(String a) {2}

    def call = { def a ->
        "a"
    }

    def call() {2}
}


def test = new Test()
print test <ref>+ 2


