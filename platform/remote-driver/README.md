# Integration Tests with Driver

`Driver` API provides a generic interface to call code in a running IDE instance, such as service and utility methods. It connects to a process via JMX protocol and creates remote proxies for classes of the running IDE. The main purpose of this API is to execute IDE actions and observe the state of the process in end-to-end testing.

## Connecting to a Running IDE

Driver uses [JMX](https://en.wikipedia.org/wiki/Java_Management_Extensions) as the underlying protocol to call IDE code. To connect to an IDE via `Driver` you need to start it with the following VM Options:
```shell
-Dcom.sun.management.jmxremote=true
-Dcom.sun.management.jmxremote.port=7777
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
-Djava.rmi.server.hostname=localhost
```

Then, you will be able to create a driver and call IDE:
```kotlin
val driver = Driver.create(JmxHost(null, null, "localhost:7777"))
assertTrue(driver.isConnected)
println(driver.getProductVersion())
driver.exitApplication()
```

## @Remote API Calls

The main use case for Driver is calling arbitrary services and utilities of IDE and plugins.

To call any code, you need to create an interface annotated with `@Remote` annotation. It must declare methods you need with the same name and number of parameters as the actual class in the IDE.
Example:
```kotlin
@Remote("com.intellij.psi.PsiManager")
interface PsiManager {
  fun findFile(file: VirtualFile): PsiFile?
}

@Remote("com.intellij.openapi.vfs.VirtualFile")
interface VirtualFile {
  fun getName(): String
}

@Remote("com.intellij.psi.PsiFile")
interface PsiFile
```

Then it can be used in the following call:
```kotlin
driver.withReadAction {
  // here we access Project-level service
  val psiFile = service<PsiManager>(project).findFile(file)
}
```

Supported types of method parameters and results:
- primitives and their wrappers Integer, Short, Long, Double, Float, Byte
- String
- Remote reference
- Array of primitive values, String or Remote references
- Collection of primitive values, String or Remote references

To use classes that are not primitives, you create the corresponding `@Remote` mapped interface and use it instead of the original types in method signatures.

If a plugin (not the platform) declares a required service/utility, you must specify the plugin identifier in `Remote.plugin` attribute:
```kotlin
@Remote("org.jetbrains.plugins.gradle.performanceTesting.ImportGradleProjectUtil", 
        plugin = "org.jetbrains.plugins.gradle")
interface ImportGradleProjectUtil {
  fun importProject(project: Project)
}
```

Only *public* methods can be called. Private, package-private and protected methods are supposed to be changed to public. Mark methods with org.`jetbrains.annotations.VisibleForTesting` to show that they are used from tests.

Service and utility proxies can be acquired on each call, there is no need to cache them in clients.

Any IDE class may have as many different `@Remote` mapped interfaces as needed, you can declare another one if the standard SDK does not provide the required method.

Please put common platform `@Remote` mappings to `intellij.driver.sdk` module under `com.intellij.driver.sdk` package.

## Contexts and Remote References

Managing references to objects that exist in another JVM process is a tricky business. Driver uses `WeakReference` for call results to not trigger a memory leak.

Let's take a look at the example:
```kotlin
val roots = driver.service<ProjectRootManager>.getContentRoots()
val name = roots[0].getName() // may throw an error
```

In many cases, it throws an exception:
> Weak reference to variable 12 expired. Please use `Driver.withContext { }` for hard variable references.

If you want to use a result later, there must be additional measures to preserve references between calls. Such measures called context boundary:
```kotlin
driver.withContext {
  val roots = service<ProjectRootManager>.getContentRoots()
  val name = roots[0].getName() // always OK!

  // results computed inside guaranteed to be alive till the end of the block
}
```

Driver supports many nested context boundaries, and you can use them independently in helper methods, e.g:
```kotlin
fun Driver.importGradleProject(project: Project? = null) {
  withContext {
    val forProject = project ?: singleProject()
    this.utility(ImportGradleProjectUtil::class).importProject(forProject)
  }
}
```

## UI Testing

Test SDK provides additional API to simplify simulation of user actions via `UiRobot`. Start with calling `driver.ui` to get `UiRobot` instance, then find UI components with XPath selectors:

```kotlin
driver.ui.welcomeScreen {
  val createNewProjectButton = x("//div[(@accessiblename='New Project' and @class='JButton')")
  createNewProjectButton.click()
}
```

Please note that `x` and `xx` methods do not perform the actual search of a UI component on screen, it will be done on first immediate action such as click or asserts via `should`:

```kotlin
val header = x("//div[@text='AI Assistant']")
header.shouldBe("AI assistant header not present", visible)
```

To simplify exploration of UIs and make XPath selectors easier to write, you can use UI hierarchy web interface. It can be enabled via a VM option `-Dexpose.ui.hierarchy.url=true`. UI hierarchy is available then from a web browser at http://localhost:63343/api/remote-driver/.

UI Robot enables you to reuse locators via a Page Object pattern: 
```kotlin
fun Finder.welcomeScreen(action: WelcomeScreenUI.() -> Unit) {
  x("//div[@class='FlatWelcomeFrame']", WelcomeScreenUI::class.java).action()
}

class WelcomeScreenUI(data: ComponentData) : UiComponent(data) {
  private val leftItems = tree("//div[@class='Tree']")

  fun clickProjects() = leftItems.clickPath("Projects")
}
```

So the usage can be simplified to:
```kotlin
driver.ui.welcomeScreen {
  clickProjects()
}
```

## Waiting

There are two ways to wait for a condition with a timeout:

1. `Awaitility` library, see https://www.baeldung.com/awaitility-testing
2. `should` methods of UI components

For common IDE states, SDK also provides the following helpers:
```kotlin
// 1. there must be an opened project and all progresses finished
waitForProjectOpen(timeout) 

// 2. all progresses must disappear from status bar
waitForIndicators(project, timeout)

// 3. daemon must finish analysis in a file
waitForCodeAnalysis(file)
```

## Bootstrapping IDE for Test

The easiest way to prepare a product under test is JUnit extension - `DriverManager`. It enables you to specify product, project, SDK, system properties and command line arguments via `DriverBuilder`.

DriverManager enables sensitive defaults including UI hierarchy available by default at http://localhost:63343/api/remote-driver/.

Example:
```kotlin
@ExtendWith(DriverManager::class)
class OpenGradleJavaFileTest {
  val driver: Driver = DriverManager.create {
    product = IdeProductProvider.IU
    project = IdeaUltimateCases.Alfio.projectInfo
    sdk = JdkDownloaderFacade.jdk11.toSdk()
  }

  @BeforeEach
  fun import() {
    driver.importGradleProject()
  }
}
```

Additionally, DriverManager can inject information about running product to test instance, such as `IDEDataPaths`. Declare a `lateinit var` with the corresponding type in a test:

```kotlin
@ExtendWith(DriverManager::class)
class SlowVisitorsPerformanceTest {
  private val driver: Driver = DriverManager.create {
    product = IdeProductProvider.IU
  }

  private lateinit var dataPaths: IDEDataPaths

  @Test
  fun test() {
    val logDir = dataPaths.testHome.resolve("log")
    println("Log folder: $log")
  }
}
```