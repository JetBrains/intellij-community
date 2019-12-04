class C {
  private myPrivateField
}

def outsideUsage(C c) {
  c.<warning descr="Access to 'myPrivateField' exceeds its access rights">myPrivateField</warning>
}
