private @groovy.transform.Field privateScriptField

private privateScriptMethod() {}

privateScriptField
this.privateScriptField
privateScriptMethod()
this.privateScriptMethod()

def scriptMethodUsage() {
  privateScriptField
  this.privateScriptField
  privateScriptMethod()
  this.privateScriptMethod()
}

def scriptMethodAnonymousUsage() {
  new Runnable() {
    @Override
    void run() {
      privateScriptField
      privateScriptMethod()
    }
  }
}
