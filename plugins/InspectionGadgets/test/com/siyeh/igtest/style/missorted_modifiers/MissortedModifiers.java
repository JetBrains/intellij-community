import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Hashtable;


class MissortedModifiers {
  <warning descr="Missorted modifiers 'private native static'">private native static</warning> int foo2();

  <warning descr="Missorted modifiers 'static private'">static private</warning> int m_bar = 4;
  <warning descr="Missorted modifiers 'static public'">static public</warning> int m_baz = 4;
  <warning descr="Missorted modifiers 'static final public'">static final public</warning> int m_baz2 = 4;
  static final int m_baz3 = 4;

  <warning descr="Missorted modifiers 'static public'">static public</warning> void foo(){}

  <warning descr="Missorted modifiers 'static public'">static public</warning> class Foo
  {

  }

  <warning descr="Missorted modifiers 'public @Deprecated'">public @Deprecated</warning> void foo3(){};
  private @ReadOnly int [] nums;

  <warning descr="Missorted modifiers 'private transient static'">private transient static</warning> Hashtable mAttributeMeta;

  interface A {

    <warning descr="Missorted modifiers 'default public'">default public</warning> double f() {
      return 0.0;
    }
  }

  <warning descr="Missorted modifiers 'final public'">final public</warning> class TestQuickFix
  {
    <warning descr="Missorted modifiers 'protected final static'">protected final static</warning> String A = "a";
    <warning descr="Missorted modifiers 'protected final static'">protected final static</warning> String B = "b";
    <warning descr="Missorted modifiers 'protected final static'">protected final static</warning> String C = "c";
    <warning descr="Missorted modifiers 'protected final static'">protected final static</warning> String D = "d";
  }

  //@Type(type = "org.joda.time.contrib.hibernate.PersistentYearMonthDay")
  //@Column(name = "current_month")
  <warning descr="Missorted modifiers 'final   public   @Nullable   // commment   @NotNull'">final
  public
  @Nullable
  // commment
  @NotNull</warning>
  int //@Temporal(TemporalType.DATE)
  x() {return -1;}
}
@Target(ElementType.TYPE_USE)
@interface ReadOnly {}