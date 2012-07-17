package com.siyeh.igtest.initialization.this_escaped_in_constructor;

import java.io.ByteArrayInputStream;
import static java.lang.System.*;

class MethodUsers extends ByteArrayInputStream {

  {
    setIn(this);
  }

  public MethodUsers(byte[] buf) {
    super(buf);
  }
}