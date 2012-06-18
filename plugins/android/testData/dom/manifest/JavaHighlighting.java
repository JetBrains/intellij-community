package p1.p2;

import java.lang.String;

public class JavaCompletion {
  public void f() {
    String s = Manifest.permission.perm1;
    s = Manifest.permission.perm2;
    s = Manifest.permission.<error>unknown</error>;
    s = Manifest.<error>permissio</error>.perm1;
    s = Manifest.permission.aba_perm;

    s = Manifest.permission_group.group1;
    s = Manifest.permission_group.<error>unknown</error>;
    s = Manifest.permission_group.aba_group;
  }
}