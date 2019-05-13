import java.util.concurrent.locks.Condition;

class WaitCalledOnCondition {

  void f(Condition c) throws InterruptedException {
    c.<warning descr="Call to 'wait()' on Condition object">wait</warning>();
  }
}