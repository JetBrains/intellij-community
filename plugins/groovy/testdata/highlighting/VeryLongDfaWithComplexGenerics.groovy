class TroubleCase {

  private Foo<Bar> fooBar;
  private Foo<Baz> fooBaz;

  private void troubleMethod(boolean b) {
    def <warning descr="Assignment is not used">icDao</warning> = (b?fooBaz:fooBar);
        
        for(Object x: new ArrayList()) {
        }
        
    }
}

public interface Foo<FFIC> {}
public class Bar implements Cloneable, Zoo<Goo, Doo, <error descr="Type parameter 'Coo' is not in its bound; should extend 'Hoo<AR>'">Coo</error>, <error descr="Type parameter 'Woo' is not in its bound; should extend 'Hoo<FR>'">Woo</error>> {}
public interface Zoo<AR, FR, AM extends Hoo<AR>, FM extends Hoo<FR>> {}
public interface Hoo<R> {}
public class Baz implements Cloneable, Zoo<String,String,<error descr="Type parameter 'Too' is not in its bound; should extend 'Hoo<AR>'">Too</error>,<error descr="Type parameter 'Yoo' is not in its bound; should extend 'Hoo<FR>'">Yoo</error>> {}
public class Goo {}
public class Too implements Hoo<String> {}
public class Coo implements Serializable, Cloneable, Hoo<Goo> {}
public class Woo implements Serializable, Cloneable, Hoo<Doo> {}
public class Yoo implements Serializable, Cloneable, Hoo<String> {}
public class Doo {}