package com.siyeh.igtest.initialization.this_escaped_in_constructor;

import java.io.ByteArrayInputStream;
import static java.lang.System.*;

class MethodUsers extends ByteArrayInputStream {

  {
    setIn(<warning descr="Escape of 'this' during object construction">this</warning>);
  }

  public MethodUsers(byte[] buf) {
    super(buf);
  }
}