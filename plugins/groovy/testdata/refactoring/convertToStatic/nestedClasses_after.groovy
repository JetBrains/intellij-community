import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

@CompileStatic
class UpClass {
  @CompileDynamic
  class InClass{
    def c = UpClass.unresolved

      @CompileStatic
      def method() {
      new I() {
        def m() {
          InClass.unresolved
        }
      }
    }
  }
}

@CompileStatic
interface I{}
