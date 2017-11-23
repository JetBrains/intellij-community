<info descr="null" textAttributesKey="Annotation">@</info><info descr="null" textAttributesKey="Annotation"><info descr="null" textAttributesKey="Annotation">Annotation</info></info>(<info descr="null" textAttributesKey="Anotation attribute name">parameter</info> = 'value')
class <info descr="null" textAttributesKey="Class">C</info> {
  <info descr="null" textAttributesKey="Groovy constructor declaration">C</info>() {
    this(1, 2)
  }

  <info descr="null" textAttributesKey="Groovy constructor declaration">C</info>(<info descr="null" textAttributesKey="Groovy parameter">a</info>, <info descr="null" textAttributesKey="Groovy parameter">b</info>) {
    super()
  }

  public <info descr="null" textAttributesKey="Instance field">instanceField</info>
  def <info descr="null" textAttributesKey="Instance field">instanceProperty</info>
  def <info descr="null" textAttributesKey="Groovy method declaration">getInstanceGetter</info>() {}
  boolean <info descr="null" textAttributesKey="Groovy method declaration">isInstanceGetterBool</info>() { false }
  void <info descr="null" textAttributesKey="Groovy method declaration">setInstanceSetter</info>(<info descr="null" textAttributesKey="Groovy parameter">p</info>) {}

  public static <info descr="null" textAttributesKey="Static field">staticField</info>
  static def <info descr="null" textAttributesKey="Static field">staticProperty</info>
  static def <info descr="null" textAttributesKey="Groovy method declaration">getStaticGetter</info>() {}
  static boolean <info descr="null" textAttributesKey="Groovy method declaration">isStaticGetterBool</info>() { false }
  static void <info descr="null" textAttributesKey="Groovy method declaration">setStaticSetter</info>(<info descr="null" textAttributesKey="Groovy parameter">p</info>) {}

  void <info descr="null" textAttributesKey="Groovy method declaration">instanceMethod</info>(<info descr="null" textAttributesKey="Groovy parameter">param</info>) {
    def <info descr="null" textAttributesKey="Groovy var">local</info> = <info descr="null" textAttributesKey="Groovy parameter">param</info>
    <info descr="null" textAttributesKey="Label">label</info>:
    for (<info descr="null" textAttributesKey="Groovy parameter">a</info> in <info descr="null" textAttributesKey="Groovy var">local</info>) {
      continue label
    }
  }

  static <<info descr="null" textAttributesKey="Type parameter">T</info>> void <info descr="null" textAttributesKey="Groovy method declaration">staticMethod</info>(<info descr="null" textAttributesKey="Type parameter">T</info> <info descr="null" textAttributesKey="Groovy reassigned parameter">reassignedParam</info>) {
    def <info descr="null" textAttributesKey="Groovy reassigned var">reassignedLocal</info> = null
    <info descr="null" textAttributesKey="Groovy reassigned parameter">reassignedParam</info> = <info descr="null" textAttributesKey="Groovy reassigned var">reassignedLocal</info>
    <info descr="null" textAttributesKey="Groovy reassigned var">reassignedLocal</info> = <info descr="null" textAttributesKey="Groovy reassigned parameter">reassignedParam</info>
  }

  void 'method with literal name'() {}
}

abstract class <info descr="null" textAttributesKey="Class">AbstractClass</info> {}
interface <info descr="null" textAttributesKey="Interface name">I</info> {}
trait <info descr="null" textAttributesKey="Trait name">T</info> {}
enum <info descr="null" textAttributesKey="Enum name">E</info> {}
@interface <info descr="null" textAttributesKey="Annotation">Annotation</info> {
  <info descr="null" textAttributesKey="Class">String</info> <info descr="null" textAttributesKey="Groovy method declaration">parameter</info>()
}

def <info descr="null" textAttributesKey="Groovy var">c</info> = new <info descr="null" textAttributesKey="Anonymous class name">C</info>() {}
<info descr="null" textAttributesKey="Groovy var">c</info>.<info descr="null" textAttributesKey="Instance field">instanceField</info>

<info descr="null" textAttributesKey="Groovy var">c</info>.<info descr="null" textAttributesKey="Instance property reference ID">instanceProperty</info>
<info descr="null" textAttributesKey="Groovy var">c</info>.<info descr="null" textAttributesKey="Instance property reference ID">instanceProperty</info> = 42
<info descr="null" textAttributesKey="Groovy var">c</info>.<info descr="null" textAttributesKey="Instance property reference ID">instanceGetter</info>
<info descr="null" textAttributesKey="Groovy var">c</info>.<info descr="null" textAttributesKey="Instance property reference ID">instanceGetterBool</info>
<info descr="null" textAttributesKey="Groovy var">c</info>.<info descr="null" textAttributesKey="Instance property reference ID">instanceSetter</info> = 42

<info descr="null" textAttributesKey="Groovy var">c</info>.<info descr="null" textAttributesKey="Method call">getInstanceProperty</info>()
<info descr="null" textAttributesKey="Groovy var">c</info>.<info descr="null" textAttributesKey="Method call">setInstanceProperty</info>(42)
<info descr="null" textAttributesKey="Groovy var">c</info>.<info descr="null" textAttributesKey="Method call">getInstanceGetter</info>()
<info descr="null" textAttributesKey="Groovy var">c</info>.<info descr="null" textAttributesKey="Method call">isInstanceGetterBool</info>()
<info descr="null" textAttributesKey="Groovy var">c</info>.<info descr="null" textAttributesKey="Method call">setInstanceSetter</info>(42)

<info descr="null" textAttributesKey="Groovy var">c</info>.<info descr="null" textAttributesKey="Method call">instanceMethod</info>()

<info descr="null" textAttributesKey="Class">C</info>.<info descr="null" textAttributesKey="Static field">staticField</info>

<info descr="null" textAttributesKey="Class">C</info>.<info descr="null" textAttributesKey="Static property reference ID">staticProperty</info>
<info descr="null" textAttributesKey="Class">C</info>.<info descr="null" textAttributesKey="Static property reference ID">staticProperty</info> = 42
<info descr="null" textAttributesKey="Class">C</info>.<info descr="null" textAttributesKey="Static property reference ID">staticGetter</info>
<info descr="null" textAttributesKey="Class">C</info>.<info descr="null" textAttributesKey="Static property reference ID">staticGetterBool</info>
<info descr="null" textAttributesKey="Class">C</info>.<info descr="null" textAttributesKey="Static property reference ID">staticSetter</info> = 42

<info descr="null" textAttributesKey="Class">C</info>.<info descr="null" textAttributesKey="Static method access">getStaticProperty</info>()
<info descr="null" textAttributesKey="Class">C</info>.<info descr="null" textAttributesKey="Static method access">setStaticProperty</info>(42)
<info descr="null" textAttributesKey="Class">C</info>.<info descr="null" textAttributesKey="Static method access">getStaticGetter</info>()
<info descr="null" textAttributesKey="Class">C</info>.<info descr="null" textAttributesKey="Static method access">isStaticGetterBool</info>()
<info descr="null" textAttributesKey="Class">C</info>.<info descr="null" textAttributesKey="Static method access">setStaticSetter</info>(42)

<info descr="null" textAttributesKey="Class">C</info>.<info descr="null" textAttributesKey="Static method access">staticMethod</info>()

<info descr="null" textAttributesKey="Groovy var">c</info>.'method with literal name'()

class <info descr="null" textAttributesKey="Class">Outer</info> {
  def <info descr="null" textAttributesKey="Groovy method declaration">getThis</info>() {}
  def <info descr="null" textAttributesKey="Groovy method declaration">getSuper</info>() {}

  class <info descr="null" textAttributesKey="Class">Inner</info> {
    def <info descr="null" textAttributesKey="Groovy method declaration">foo</info>() {
      this
      super.<info descr="null" textAttributesKey="Method call">hashCode</info>()
      <info descr="null" textAttributesKey="Class">Outer</info>.<info descr="null" textAttributesKey="GROOVY_KEYWORD">this</info>
      <info descr="null" textAttributesKey="Class">Outer</info>.<info descr="null" textAttributesKey="GROOVY_KEYWORD">super</info>.<info descr="null" textAttributesKey="Method call">toString</info>()
    }
  }
}

def <info descr="null" textAttributesKey="Groovy var">outer</info> = new <info descr="null" textAttributesKey="Class">Outer</info>()
<info descr="null" textAttributesKey="Groovy var">outer</info>.<info descr="null" textAttributesKey="Instance property reference ID">this</info>
<info descr="null" textAttributesKey="Groovy var">outer</info>.<info descr="null" textAttributesKey="Instance property reference ID">super</info>
