class Target {
  private static void callMe() {}
}

@groovy.transform.CompileStatic
void csUsage() {
  Target.<error descr="Access to 'callMe' exceeds its access rights">callMe</error>()
}
