class Foo extends GroovyObjectSupport{
  def se

  def a() {
    se<caret>
  }
}

public abstract class GroovyObjectSupport  {
    public Object getProperty(String property) {
      null
    }

    public void setProperty(String property, Object newValue) {
    }

    public Object invokeMethod(String name, Object args) {
      null
    }

    public MetaClass getMetaClass() {
      null
    }

    public void setMetaClass(MetaClass metaClass) {
    }
}
