// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.execution.ShortenCommandLine
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.task.ExecuteRunConfigurationTask

/**
 * @author Vladislav.Soroka
 */
open class GradleApplicationEnvironmentProvider : GradleBaseApplicationEnvironmentProvider<ApplicationConfiguration>() {
  override fun isApplicable(task: ExecuteRunConfigurationTask): Boolean = task.runProfile is ApplicationConfiguration

  override fun generateInitScript(params: GradleInitScriptParameters): String? {
    val shortenCommandLine = params.configuration.shortenCommandLine
    val useManifestJar = shortenCommandLine === ShortenCommandLine.MANIFEST
    val useArgsFile = shortenCommandLine === ShortenCommandLine.ARGS_FILE
    var useClasspathFile = shortenCommandLine === ShortenCommandLine.CLASSPATH_FILE
    var intelliJRtPath: String? = null
    if (useClasspathFile) {
      try {
        intelliJRtPath = FileUtil.toCanonicalPath(
          PathManager.getJarPathForClass(Class.forName("com.intellij.rt.execution.CommandLineWrapper")))
      }
      catch (t: Throwable) {
        LOG.warn("Unable to use classpath file", t)
        useClasspathFile = false
      }
    }

    // @formatter:off
    // @Language("Groovy")
    val initScript = """
    def gradlePath = '${params.gradleTaskPath}'
    def runAppTaskName = '${params.runAppTaskName}'
    def mainClassToRun = '${params.mainClass}'
    def javaExePath = mapPath('${params.javaExePath}')
    def _workingDir = ${if (params.workingDirectory.isNullOrEmpty()) "null\n" else "mapPath('${params.workingDirectory}')\n"}
    def sourceSetName = '${params.sourceSetName}'
    def javaModuleName = ${if (params.javaModuleName == null) "null\n" else "'${params.javaModuleName}'\n"}
    def isOlderThan64 = GradleVersion.current().baseVersion < GradleVersion.version("6.4")
    def isOlderThan33 = GradleVersion.current().baseVersion < GradleVersion.version("3.3")
    ${if (useManifestJar) "gradle.addListener(new ManifestTaskActionListener(runAppTaskName))\n" else ""}
    ${if (useArgsFile) "gradle.addListener(new ArgFileTaskActionListener(runAppTaskName))\n" else ""}
    ${if (useClasspathFile && intelliJRtPath != null) "gradle.addListener(new ClasspathFileTaskActionListener(runAppTaskName, mainClassToRun, mapPath('$intelliJRtPath')))\n " else ""}

    import org.gradle.util.GradleVersion

    allprojects {
      afterEvaluate { project ->
        def projectPath
        if (isOlderThan33) {
          projectPath = project.path
        } else {
          projectPath = project.identityPath.toString()
        }
        if(projectPath == gradlePath && project?.convention?.findPlugin(JavaPluginConvention)) {
          def overwrite = project.tasks.findByName(runAppTaskName) != null
          project.tasks.create(name: runAppTaskName, overwrite: overwrite, type: JavaExec) {
            if (javaExePath) executable = javaExePath
            if (isOlderThan64) {
              main = mainClassToRun
            } else {
              mainClass = mainClassToRun
            }
            ${params.params}
            if (_workingDir) workingDir = _workingDir
            standardInput = System.in
            if (javaModuleName) {
              classpath = tasks[sourceSets[sourceSetName].jarTaskName].outputs.files + project.sourceSets[sourceSetName].runtimeClasspath;
              if (isOlderThan64) {
                doFirst {
                  jvmArgs += [
                    '--module-path', classpath.asPath,
                    '--module', javaModuleName + '/' + mainClassToRun
                  ]
                  classpath = files()
                }
              } else {
                mainModule = javaModuleName
              }
            } else {
              classpath = project.sourceSets[sourceSetName].runtimeClasspath
            }
          }
        }
      }
    }
    """ + (if (useManifestJar || useArgsFile || useClasspathFile) """
    import org.gradle.api.execution.TaskActionListener
    import org.gradle.api.Task
    import org.gradle.api.tasks.JavaExec
    abstract class RunAppTaskActionListener implements TaskActionListener {
      String myTaskName
      File myClasspathFile
      RunAppTaskActionListener(String taskName) {
        myTaskName = taskName
      }
      void beforeActions(Task task) {
        if(!(task instanceof JavaExec) || task.name != myTaskName) return
        myClasspathFile = patchTaskClasspath(task)
      }
      void afterActions(Task task) {
        if(myClasspathFile != null) { myClasspathFile.delete() }
      }
      abstract File patchTaskClasspath(JavaExec task)
    }
    """ else "") + (if (useManifestJar) """

    import org.gradle.api.tasks.JavaExec
    import java.util.jar.Attributes
    import java.util.jar.JarOutputStream
    import java.util.jar.Manifest
    import java.util.zip.ZipEntry
    class ManifestTaskActionListener extends RunAppTaskActionListener {
      ManifestTaskActionListener(String taskName) {
         super(taskName)
      }
      File patchTaskClasspath(JavaExec task) {
        Manifest manifest = new Manifest()
        Attributes attributes = manifest.getMainAttributes()
        attributes.put(Attributes.Name.MANIFEST_VERSION, '1.0')
        attributes.putValue('Class-Path', task.classpath.files.collect {it.toURI().toURL().toString()}.join(' '))
        File file = File.createTempFile('generated-', '-manifest')
        def oStream = new JarOutputStream(new FileOutputStream(file), manifest)
        oStream.putNextEntry(new ZipEntry('META-INF/'))
        oStream.close()
        task.classpath = task.project.files(file.getAbsolutePath())
        return file
      }
    }
    """ else "") + (if (useArgsFile) """

    import org.gradle.api.tasks.JavaExec
    import org.gradle.process.CommandLineArgumentProvider
    class ArgFileTaskActionListener extends RunAppTaskActionListener {
      ArgFileTaskActionListener(String taskName) {
         super(taskName)
      }
      File patchTaskClasspath(JavaExec task) {
        File file = File.createTempFile('generated-', '-argFile')
        def writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), 'UTF-8'))
        def lineSep = System.getProperty('line.separator')
        writer.print('-classpath' + lineSep)
        writer.print(quoteArg(task.classpath.asPath))
        writer.print(lineSep)
        writer.close()
        task.jvmArgs('@' + file.absolutePath)
        task.classpath = task.project.files([])
        return file
      }
      private static String quoteArg(String arg) {
        String specials = ' #\'\"\n\r\t\f'
        if (specials.find { arg.indexOf(it) != -1 } == null) return arg
        StringBuilder sb = new StringBuilder(arg.length() * 2)
        for (int i = 0; i < arg.length(); i++) {
          char c = arg.charAt(i)
          if (c == ' ' as char || c == '#' as char || c == '\'' as char) sb.append('"').append(c).append('"')
          else if (c == '"' as char) sb.append("\"\\\"\"")
          else if (c == '\n' as char) sb.append("\"\\n\"")
          else if (c == '\r' as char) sb.append("\"\\r\"")
          else if (c == '\t' as char) sb.append("\"\\t\"")
          else if (c == '\f' as char) sb.append("\"\\f\"")
          else sb.append(c)
        }
        return sb.toString()
      }}
    """ else "") + if (useClasspathFile) """

    import org.gradle.api.tasks.JavaExec
    import org.gradle.process.CommandLineArgumentProvider
    class ClasspathFileTaskActionListener extends RunAppTaskActionListener {
      String myMainClass
      String myIntelliJRtPath
      ClasspathFileTaskActionListener(String taskName, String mainClass, String intelliJRtPath) {
         super(taskName)
         myMainClass = mainClass
         myIntelliJRtPath = intelliJRtPath
      }
      File patchTaskClasspath(JavaExec task) {
        File file = File.createTempFile('generated-', '-classpathFile')
        def writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), 'UTF-8'))
        task.classpath.files.each { writer.println(it.path) }
        writer.close()
        List args = [file.absolutePath, myMainClass] as List
        args.addAll(task.args)
        task.args = []
        task.argumentProviders.add({ return args } as CommandLineArgumentProvider)
        task.main = 'com.intellij.rt.execution.CommandLineWrapper'
        task.classpath = task.project.files([myIntelliJRtPath])
        return file
      }
    }
    """ else ""
    // @formatter:on
    return initScript
  }

  companion object {
    private val LOG = logger<GradleApplicationEnvironmentProvider>()
  }
}
