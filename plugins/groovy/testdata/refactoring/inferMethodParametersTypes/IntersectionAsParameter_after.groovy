def <T extends I & J> Object foo(T a) {
  bar(a)
}

interface I {}
interface J {}

def <T extends I&J> void bar(T x) {}
