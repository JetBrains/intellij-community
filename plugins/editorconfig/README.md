# IntelliJ IDEA Community Edition

This is the official GitHub mirror of the [IntelliJ IDEA Community Edition](http://www.jetbrains.com/idea/) and
[PyCharm Community Edition](http://www.jetbrains.com/pycharm/) source code.

## Building

To develop IntelliJ IDEA, you can use either IntelliJ IDEA Community Edition or IntelliJ IDEA Ultimate. To build and run the code:

* Make sure you have the Groovy plugin enabled. Parts of IntelliJ IDEA are written in Groovy, and you will get compilation errors if you don't have the plugin enabled.
* Make sure you have the UI Designer plugin enabled. Most of IntelliJ IDEA's UI is built using the UI Designer, and the version you build will not run correctly if you don't have the plugin enabled.
* Open the directory with the source code as a directory-based project
* Configure a JSDK named "IDEA jdk", pointing to an installation of JDK 1.6
* On Windows or Linux, add lib\tools.jar from the JDK installation directory to the classpath of IDEA jdk
* Use Build | Make Project to build the code
* To run the code, use the provided shared run configuration "IDEA".

To build the distribution archive of IntelliJ IDEA Community Edition, execute build.xml Ant build script in the root directory of the
source code. The results of the build execution can be found at out/artifacts.

## Contributing

Pull requests are welcome. Please make sure that you follow the [IntelliJ Coding Guidelines](http://www.jetbrains.org/display/IJOS/IntelliJ+Coding+Guidelines).
Note that you'll need to submit a [Contributor Agreement](http://www.jetbrains.org/display/IJOS/Contributor+Agreement) before we can accept your pull request.

See http://www.jetbrains.org/ for more information.

## Developer Documentation

You can find information on the internal architecture of IntelliJ IDEA and plugin development at the
[PluginDevelopment](http://confluence.jetbrains.com/display/IDEADEV/PluginDevelopment) site.
