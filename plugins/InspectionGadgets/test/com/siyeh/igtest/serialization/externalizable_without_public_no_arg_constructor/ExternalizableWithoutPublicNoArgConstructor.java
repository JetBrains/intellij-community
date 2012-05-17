package com.siyeh.igtest.serialization.externalizable_without_public_no_arg_constructor;

import java.io.*;

abstract class e implements Externalizable {

  protected e() {}
}
class eImpl extends e {
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  }

  public void writeExternal(ObjectOutput out) throws IOException {
  }
}
class eImpl1 extends e {
  private eImpl1() {}
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  }

  public void writeExternal(ObjectOutput out) throws IOException {
  }
}
class eImpl2 extends e {
  public eImpl2(int i) {
    System.out.print(i);
  }
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  }

  public void writeExternal(ObjectOutput out) throws IOException {
  }
}

