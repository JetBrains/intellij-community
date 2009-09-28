import java.lang.*

class ScriptRunnerProxyy {
  private static ClassLoader scriptLoader
  private static Class scriptRunnerClass = scriptLoader.loadClass("org.spockframework.webconsole.ScriptRunner")

  String run(String scriptText) {
    def runner = scriptRunnerClass.newInstance()
    runner.run(scriptText)
  }
}