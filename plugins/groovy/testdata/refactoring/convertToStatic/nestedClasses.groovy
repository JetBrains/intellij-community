class UpClass {
  class InClass{
    def c = UpClass.unresolved

    def method() {
      new I() {
        def m() {
          InClass.unresolved
        }
      }
    }
  }
}

interface I{}
