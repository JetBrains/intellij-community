def m = [a: 1, b: 2]
def <warning descr="Assignment is not used">m1</warning> = [a: 1, b: 2]
def val = 'a'

switch (val) {
  case m: "key in map"; break
// equivalent to case { val in m }: ...
  default: "not in map"
}