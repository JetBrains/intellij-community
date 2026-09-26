def a = 1

def b = 'one'

a.with {
  b.with {
    print intV<caret>alue()
  }
}