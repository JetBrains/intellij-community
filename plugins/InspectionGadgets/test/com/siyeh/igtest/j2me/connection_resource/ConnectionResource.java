class ConnectionResource {

  void m() throws java.io.IOException {
    javax.microedition.io.Connection c = javax.microedition.io.Connector.open("test");
    try {

    } finally {
      c.close();
    }
  }

  void a() throws java.io.IOException {
    javax.microedition.io.Connection c = javax.microedition.io.Connector.<warning descr="'Connection' should be opened in front of a 'try' block and closed in the corresponding 'finally' block">open</warning>("test");
  }

}