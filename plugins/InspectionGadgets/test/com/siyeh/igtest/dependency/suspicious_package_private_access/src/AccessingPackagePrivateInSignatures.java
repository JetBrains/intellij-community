package xxx;

public class AccessingPackagePrivateInSignatures implements Runnable {
  @Override
  public void run() {
    PackagePrivateInSignatures foo = new PackagePrivateInSignatures();
    foo.inParam(<warning descr="Interface xxx.PackagePrivateInterface is package-private, but declared in a different module 'dep'">() -> "hello"</warning>);
  }
}