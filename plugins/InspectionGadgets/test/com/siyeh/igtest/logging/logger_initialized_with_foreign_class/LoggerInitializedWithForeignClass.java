import java.util.logging.*;

class LoggerInitializedWithForeignClass {

  void foo() {
    new Object() {
      void bar() {
        Logger.getLogger(LoggerInitializedWithForeignClass.class.getName());
        Logger.getLogger(<warning descr="Logger initialized with foreign class 'String.class'">String.class</warning>.getName());
      }
    };
  }
}