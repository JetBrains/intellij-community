def map = new HashMap<String, String>()

print (map["1"] = "6")
print (map[2] = "7")
map["6"] = 1

print map["1"]
print map[2]

class Foo {
  def putAt(String s, Integer x, Object value) {

  }

  def getAt(String s, Integer x) {
    return new Object()
  }
}

def foo = new Foo()
foo["a", 2] =4
print (foo["a", 2] = 4)

print foo['b', 1]
print foo['4']

int[] arr = [1, 2, 3]
print arr[1]
arr[1] = 3
