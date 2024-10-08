class A {
  void x() {
    def b = """\
this is "good"\
"""
    def c = '\"\"'
    def d = " \
"
    def data = [
      "\"\\\\\""        : "\"\\\\\""
    ]
  }
}