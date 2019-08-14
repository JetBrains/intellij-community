def foo(a) {
  bar(a)
}

interface I {}
interface J {}

def <T extends I&J> void bar(T x) {}
