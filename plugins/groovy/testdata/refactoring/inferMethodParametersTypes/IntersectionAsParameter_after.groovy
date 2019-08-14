def <T0 extends I & J> Object foo(T0 a) {
  bar(a)
}

interface I {}
interface J {}

def <T extends I&J> void bar(T x) {}
