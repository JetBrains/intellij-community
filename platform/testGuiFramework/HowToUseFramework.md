#How to Use Test GUI Framework

Hello. This module serves for GUI testing purposes (e.g. smoke tests, UI tests, high-level integration tests). The core of UI testing framework 
has been forked from Android Studio (thanks to Google colleagues), filled with IDEA specific fixtures and supplemented by kotlin DSL.    

The framework consists of 3 parts:
* The framework itself
* Running infrastructure (launchers and runners)
* GUI Test Recorder (to be defined later...)


####Framework
As it said before, framework has been forked from Android Studio (version 2.0) for UI testing IntelliJ. It based on FEST framework (Framework Easy Swing 
Testing developed by Alex Ruiz <https://github.com/alexruiz/fest-swing-1.x>, updating stopped in 2013), to operate with a standard 
UI components from Swing and extended by custom fixtures, matchers and utilities. 

FEST-Swing framework consists of different parts: 
- fixtures: class-wrappers to operate with Swing components
- finders: serve to find specific component in hierarchy
- matchers: classes describe how to match the component in hierarchy
- drivers: peer-classes delegating mouse and keyboard actions to original components

FEST simulates user actions (mouse and keyboard) by `org.fest.swing.core.Robot` based on `java.awt.Robot`. All UI event generations are encapsulated 
in FEST framework, so our purpose is to enlarge fixtures set with IDE-specific components and provide useful testcase fixtures for test writers.

Let's take a look inside the structure of module related to framework:
- [com.intellij.testGuiFramework](src/com/intellij/testGuiFramework)
  - [cellReader](src/com/intellij/testGuiFramework/cellReader) contains custom readers for list-like and tree-like classes. Unfortunately IntelliJ 
  IDEA has a huge number of custom classes implemented list or tree. So it could be a problem to find suitable leaf in a tree if you know only
  a visible text of it. 
  - [driver](src/com/intellij/testGuiFramework/driver) contains a custom drivers implemented UI actions. Drivers cover all UI interactions routine 
  and object model (like clicking coordinates) inside of it. If you need to operate with fixture in a different manner just replace driver with
  your own. 
  - [finder](src/com/intellij/testGuiFramework/finder) could be used to hold custom finders. 
  - [fixtures](src/com/intellij/testGuiFramework/fixtures) contains fixtures for a custom components of InteliiJ platform. Fixtures mostly derived 
  from `AbstractComponentFixture`. Any fixture holds component, has a driver to operate with original component and reference to robot instance.
  - [framework](src/com/intellij/testGuiFramework/framework) contains a basement to run UI tests. We'll take a look in it more carefully later.    
  - [matcher](src/com/intellij/testGuiFramework/matcher) contains custom matchers for finder.

####Running infrastructure
Let's assume that we have written UI tests. The next step is to launch them. We are supporting next scenarios:
* Run the test with IDE from compiled sources
  * Run test from generated context configuration (right click on test code or click on run icon in the gutter)
  * Run test with  (or from control test) started IDE 
* Run the test with installed IDE

#####Running test from context configuration
To run GUI test from context run configuration just right click on editor with opened test and select between run or debug. It also possible 
to run test by clicking icon on the gutter of opened editor. All test are running with IntelliJ IDEA Community from source code. If you need 
to start with other IntelliJ-based IDE just add `@RunWithIde()` and specify IDE with [`IdeType`](src/com/intellij/testGuiFramework/launcher/ide/IdeType.kt) annotation before test class: 
```kotlin
@RunWithIde(WebStormIde::class)
class SomeWebStormTest : GuiTestCase()
```  

JUnit framework starts GUI test. As all GUI tests are derived from from [`GuiTestCase`](src/com/intellij/testGuiFramework/impl/GuiTestCase.kt)
JUnit uses custom runner: [`GuiTestLocalRunner`](src/com/intellij/testGuiFramework/framework/GuiTestLocalRunner.kt).
1. `GuiTestLocalRunner` starts [`JUnitServer`](src/com/intellij/testGuiFramework/remote/server/JUnitServer.kt) to send tests and receive test states.
2. `JUnitServer` open a port to accept connection from IDE.
3. `GuiTestLocalRunner` runs IDE, which is described in GUI test class annotation: `@RunWithIde(CommunityIde::class)`. 
4. [`GuiTestLocalLauncher`](src/com/intellij/testGuiFramework/launcher/GuiTestLocalLauncher.kt) composes classpath from the main module of 
specified IDE, `testGuiFramework` module and test. Than starts IDE with additional arguments `guitest port={a port from JUnitServer}`. 
5. IDE starts with [`JUnitClient`](src/com/intellij/testGuiFramework/remote/client/JUnitClient.kt) and connects to `JUnitServer`. `JUnitClient`
receives from `JUnitServer` tests to run and put them into `testQueue` which are consumed by [`GuiTestThread`](src/com/intellij/testGuiFramework/impl/GuiTestThread.kt).

#####Running test with manually started IDE
Here we should add definition of **Control test**. Control tests are used for running IDE once for multiple tests. All you need is to have a 
GUI test. And write a GUI test derived from [`RemoteTestCase`](src/com/intellij/testGuiFramework/remote/RemoteTestCase.kt). The syntax of control 
tests is very simple:  

```kotlin
import com.intellij.testGuiFramework.launcher.ide.Ide
import com.intellij.testGuiFramework.launcher.ide.IdeType
import com.intellij.testGuiFramework.remote.RemoteTestCase
import org.junit.Test

class ControlTest: RemoteTestCase() {

  @Test
  fun testColorSchemeTest() {
    val ide = Ide(IdeType.IDEA_ULTIMATE, 0, 0) // if path is not specified than run current IntelliJ IDEA from compiled sources
    startAndClose(ide) {
      runTest("com.intellij.testGuiFramework.tests.ColorSchemeTest#testFail")
      runTest("com.intellij.testGuiFramework.tests.ColorSchemeTest#testColorScheme")
      runTest("com.intellij.testGuiFramework.tests.ColorSchemeTest#testColorScheme2")
    }
  }
}
```

`startAndClose` method launches specified **ide** from compiled sources and than perform methods inside lambda code block (`runTest` in this case). 
It works very similar to **Running test from context configuration** section. There are also JUnitServer and JUnitClient communication under the hood. 

#####Running test with installed IDE
Before running this method it is needed to add TestGuiFramework plugin to installed IDE. 

For running test with already installed IDE we also use *Control test* from previous (Running test with manually started IDE) part. The main difference
is to specify path to IDE explicitly. To run tests by this method, copy test class to installed IDE lib dir, to add this class to classpath.

```kotlin
  @Test
  fun testColorSchemeTest() {
    val ide = Ide(IdeType.IDEA_ULTIMATE, 0, 0) // if path is not specified than run current IntelliJ IDEA from compiled sources
    startAndClose(ide, "/Users/jetbrains/Library/Application Support/JetBrains/Toolbox/apps/IDEA-U/ch-0/172.2300/IntelliJ IDEA 2017.2 EAP.app") {
      TODO("add test here")   
    }
  } 
```

###How to write GUI Tests?
All UI tests should be derived from [`GuiTestCase`](src/com/intellij/testGuiFramework/impl/GuiTestCase.kt) class. `GuiTestCase` class has a 
bold kotlin DSL wrapping support for writing GUI tests for IntelliJ-based IDEs. Let's take a look structure of this DSL:

####Contexts
When you trying to define what component you want to be interacted it is needed to define a context – what window, what dialog etc. `GuiTestCase`
allows to use next contexts:
 
**Global contexts**:
- `welcomeFrame` context of Welcome screen panel.
- `ideFrame` context of the main frame for IDE
- `dialog` a particular dialog which can appear in a context of welcome frame or IDE frame  

**Local contexts**:
- `toolWindow` context of tool window in the IDE frame. Also one tool window can have several contexts
- `editor` context of the current active editor in the IDE frame
- `projectView` a specific context of project view tool window. It has different methods from a _toolWindow*

####Fixtures
Fixture is a wrapper-class to cover all interaction with a component and EDT-thread actions from test writer. All fixtures use `ComponentDriver`
to delegate UI actions from test to event-queue properly. If you need to override UI component interaction, just extend already existed component 
driver or write your own. [`GuiTestCase`](src/com/intellij/testGuiFramework/impl/GuiTestCase.kt) provides easy methods to instantiate proper fixture for the vast majority oj IDE UI components. Let's 
highlight them:
- `button()` - wrapper for a button fixture (see org.fest.swing.fixture.JButtonFixture.class). Could be found by a button text. Button 
interacts with a AbstractButtonDriver by default. If you want to find some button and click it just use: 
  ```kotlin
  button("OK").click()
  ```
- `actionLink()` - wrapper for a [`ActionLinkFixture`](src/com/intellij/testGuiFramework/fixtures/ActionLinkFixture.java). Could be found by 
  an aciton link text. A simple example of action link is Welcome frame where *Create New Project*, *Open*, ... are action links. As a 
  JComponentFixture, ActionLinkFixture has method `click()` to click on it. 
- `jTree()` - wrapper for a JTreeFixture (see org.fest.swing.fixture.JTreeFixture.class). If some IDEA component container (e.g. dialog or 
  IdeFrame) has more than one JTree you should to specify the finding by it path. Path items should be divided by coma: 
  ```kotlin
  jTree("Editor", "Colors & Fonts", "Java")
  ```
  jTree allow to use `clickPath(<path_separated_by_comma>)`, `doubleClickPath(<path_separated_by_comma>)` and `rightClickPath(<path_separated_by_comma>)`
  to manipulate with nodes. We are also using an extended class for JTreeFixture [`ExtendedTreeFixture`](src/com/intellij/testGuiFramework/fixtures/extended/ExtendedTreeFixture.kt) 
  to encapsulate recognition of the most common IDEA jTree nodes text. If some node couldn't be read or find try to add this case to 
  [`ExtendedJTreeDriver`](src/com/intellij/testGuiFramework/driver/ExtendedJTreeDriver.kt).
- `jList()` - wrapper for a JListFixture (see org.fest.swing.fixture.JListFixture.class). It is similar to `jTree()` – jList could be found 
  by item name to distinguish more than one JList instances in one container. If you need to click specified item in JList just call 
  `clickItem()` method on *JListFixture*:
  ```kotlin
    jList("Command Line App").clickItem("Command Line App")
  ```  
- `popup()` - this method is used for selecting menu path (e.g. context menu).
- `typeText()` - this method is used to type text in focused component, like enter project name, or even write some code in editor
- `combobox()` - wrapper for a JComboBoxFixture (see org.fest.swing.fixture.JComboBoxFixture). Could be found by label nearby combobox. Use 
  `selectItem()` method to expand and select item in combobox:
  ```kotlin
    combobox("Interpreter:").selectItem("/Library/Frameworks/Python.framework/Versions/2.7/bin/python2.7")
  ```
- `checkbox()` - wrapper for a [`CheckBoxFixture`](src/com/intellij/testGuiFramework/fixtures/CheckBoxFixture.java). Could be found by a text.
  Supports `click()` method: 
  ```kotlin
    checkbox("Create project from template").click()
  ```
- `actionButton()` - wrapper for an [`ActionButtonFixture`](src/com/intellij/testGuiFramework/fixtures/ActionButtonFixture.java). Could be 
  found by action id. Action buttons are mostly used in toolbars. Have the same behaviour to regular buttons: 
  ```kotlin
    actionButton("Print").click()
  ```
- `textfield()` - wrapper for a JTextComponentFixture (see org.fest.swing.fixture.JTextComponentFixture). Could be found by a bounded label
  text.
- `linkLabel()` - wrapper to work with LinkLabels. Could be found by bounded label text.


With a given kotlin DSL GUI tests have a next structure:

```kotlin
package com.intellij.testGuiFramework.tests.samples

import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.launcher.ide.IdeType

/**
 * All GUI tests should be derived from GuiTestCase class. It allows to use kotlin DSL for compact writing tests and to perform tests from
 * the code directly or remotely. All tests are running with IntelliJ IDEA Community Edition by default. To change IDE test class should be
 * annotated with @com.intellij.testGuiFramework.framework.RunWithIde annotation. Check com.intellij.testGuiFramework.launcher.ide.IdeType
 */
@RunWithIde(WebStormIde::class)
class SomeGuiTest: GuiTestCase() {

  @org.junit.Test           // test function should be annotated with @Test annotation
  fun testSome() {
    welcomeFrame {          // here is a lambda function with receiver. Code inside brackets is used under a context of WelcomeFrameFixture.
                            // All fixtures has a waiting time to be found. To change this time use var GuiTestCase.defaultTimeout in seconds.

      checkoutFrom()        // Use WelcomeFrameFixture methods from here like this.
      popupClick("Git")
      dialog("Clone Repository") {      // here is a DialogFixture context. TestGuiFramework is waiting until dailog with title 'Clone
                                        // Repository' appeared.
        typeText("git_url")
        textfield("Directory Name:").click()
        typeText("dir_name")
        button("Clone").click()
      }
    }

    ideFrame {                          // IdeFrameFixture context. TestGuiFramework waits until IdeFrame appears.
      waitForBackgroundTasksToFinish()  // wait until project indexing is ready.

      projectView {                     // open project view to navigate in project tree
        path("<project_name>","src", "<filename>").doubleClick() //open file in editor by double clicking on corresponding project view node
      }

      editor {   // EditorFixture gives us an access to an active Editor
        val content = getCurrentFileContents(false) // get content without caret position and selection position
        // compare content with a custom comparator
      }

      closeProject() // close project and return to welcome frame state
    }
  }
} 
```
